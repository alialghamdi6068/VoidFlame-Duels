package net.voidflame.duels;

import net.voidflame.core.api.ArenaService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ArenaManager {
    private final VoidFlameDuelsPlugin plugin;
    private final Set<String> localInUse = new HashSet<>();
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
            localInUse.add(name);
            return new Arena(name, a, b);
        }
        return null;
    }

    public synchronized CompletableFuture<Boolean> reset(Arena arena) {
        if (arena == null) return CompletableFuture.completedFuture(false);
        ensureConnected();
        if (provider != null) {
            return provider.reset(arena.name()).whenComplete((ignored, error) -> {
                synchronized (this) { localInUse.remove(arena.name()); }
            });
        }
        localInUse.remove(arena.name());
        return CompletableFuture.completedFuture(true);
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
