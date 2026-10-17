package net.voidflame.duels;

import net.voidflame.core.api.ArenaService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ArenaManager {
    private final VoidFlameDuelsPlugin plugin;
    private final Set<String> localInUse = new HashSet<>();
    private final Map<String, RegionSnapshot> localSnapshots = new HashMap<>();
    private ArenaService provider;

    public ArenaManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        connect();
    }

    public synchronized void connect() {
        RegisteredServiceProvider<ArenaService> registration =
                Bukkit.getServicesManager().getRegistration(ArenaService.class);
        provider = registration == null ? null : registration.getProvider();
    }

    public synchronized Arena acquire() { return acquire(null); }

    public synchronized Arena acquire(KitType kit) {
        ensureConnected();
        if (provider != null) {
            Optional<ArenaService.ArenaHandle> result = kit == null
                    ? provider.acquireHandle()
                    : provider.acquireHandleForKit(kit.name().toLowerCase(Locale.ROOT));
            if (result.isPresent()) {
                ArenaService.ArenaHandle handle = result.get();
                if (handle.spawnA() != null && handle.spawnB() != null) {
                    return new Arena(handle.name(), handle.spawnA(), handle.spawnB());
                }
            }
        }

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("arenas");
        if (root == null) return null;
        List<Map<?, ?>> configured = root.getMapList("list");
        if (configured.isEmpty()) return null;

        for (Map<?, ?> raw : configured) {
            String name = string(raw.get("name"));
            String worldName = string(raw.get("world"));
            if (name == null || worldName == null || localInUse.contains(name)) continue;
            Location a = location(raw, "spawn-a", worldName);
            Location b = location(raw, "spawn-b", worldName);
            if (a == null || b == null) continue;
            RegionSnapshot snapshot = snapshot(raw, worldName);
            if (snapshot == null) {
                plugin.getLogger().warning("Arena '" + name + "' is missing a valid reset region or exceeds arenas.max-reset-blocks; it will not be allocated.");
                continue;
            }
            localSnapshots.put(name, snapshot);
            localInUse.add(name);
            return new Arena(name, a, b);
        }
        return null;
    }

    public synchronized CompletableFuture<Boolean> reset(Arena arena) {
        if (arena == null) return CompletableFuture.completedFuture(false);
        ensureConnected();
        if (provider != null) {
            return provider.reset(arena.name()).thenApply(success -> {
                if (Boolean.TRUE.equals(success)) {
                    synchronized (this) {
                        localInUse.remove(arena.name());
                        localSnapshots.remove(arena.name());
                    }
                }
                return Boolean.TRUE.equals(success);
            });
        }
        RegionSnapshot snapshot = localSnapshots.get(arena.name());
        if (snapshot == null) {
            plugin.getLogger().severe("Cannot reset arena '" + arena.name()
                    + "': no safe pre-match region snapshot exists. Arena remains unavailable.");
            return CompletableFuture.completedFuture(false);
        }
        try {
            snapshot.restore();
            localSnapshots.remove(arena.name());
            localInUse.remove(arena.name());
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Failed to restore arena '" + arena.name()
                    + "'. Arena remains unavailable: " + ex.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    public synchronized int available() {
        ensureConnected();
        if (provider != null) return (int) provider.availableCount();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("arenas");
        if (root == null) return 0;
        int total = root.getMapList("list").size();
        return Math.max(0, total - localInUse.size());
    }

    public synchronized List<String> allNames() {
        ensureConnected();
        if (provider != null) return List.copyOf(provider.allNames());
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("arenas");
        if (root == null) return List.of();
        List<String> names = new ArrayList<>();
        for (Map<?, ?> raw : root.getMapList("list")) {
            String name = string(raw.get("name"));
            if (name != null) names.add(name);
        }
        return List.copyOf(names);
    }

    private void ensureConnected() {
        if (provider == null) connect();
    }

    private RegionSnapshot snapshot(Map<?, ?> raw, String worldName) {
        Object value = raw.get("region");
        if (!(value instanceof Map<?, ?> region)) return null;
        Object minValue = region.get("min");
        Object maxValue = region.get("max");
        if (!(minValue instanceof Map<?, ?> min) || !(maxValue instanceof Map<?, ?> max)) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        try {
            int minX = integer(min.get("x")), minY = integer(min.get("y")), minZ = integer(min.get("z"));
            int maxX = integer(max.get("x")), maxY = integer(max.get("y")), maxZ = integer(max.get("z"));
            if (minX > maxX || minY > maxY || minZ > maxZ
                    || minY < world.getMinHeight() || maxY >= world.getMaxHeight()) return null;
            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            long limit = Math.max(1L, plugin.getConfig().getLong("arenas.max-reset-blocks", 100_000L));
            if (volume <= 0 || volume > limit) return null;
            return RegionSnapshot.capture(world, minX, minY, minZ, maxX, maxY, maxZ, (int) volume);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid arena region coordinate", ex);
        }
    }

    private static final class RegionSnapshot {
        private final World world;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private final BlockState[] states;

        private RegionSnapshot(World world, int minX, int minY, int minZ,
                               int maxX, int maxY, int maxZ, BlockState[] states) {
            this.world = world;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            this.states = states;
        }

        static RegionSnapshot capture(World world, int minX, int minY, int minZ,
                                      int maxX, int maxY, int maxZ, int volume) {
            BlockState[] states = new BlockState[volume];
            int index = 0;
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        states[index++] = world.getBlockAt(x, y, z).getState();
                    }
                }
            }
            return new RegionSnapshot(world, minX, minY, minZ, maxX, maxY, maxZ, states);
        }

        void restore() {
            int index = 0;
            boolean complete = true;
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        try {
                            if (!states[index].update(true, false)) complete = false;
                        } catch (RuntimeException ex) {
                            complete = false;
                        }
                        index++;
                    }
                }
            }
            if (!complete) {
                throw new IllegalStateException("One or more arena blocks could not be restored.");
            }
        }
    }

    private Location location(Map<?, ?> raw, String key, String worldName) {
        Object value = raw.get(key);
        if (!(value instanceof Map<?, ?> map)) return null;
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = number(map.get("x"), 0.5);
        double y = number(map.get("y"), world.getSpawnLocation().getY());
        double z = number(map.get("z"), 0.5);
        float yaw = (float) number(map.get("yaw"), 0);
        float pitch = (float) number(map.get("pitch"), 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
