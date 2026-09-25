package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaManager {
    private final VoidFlameDuelsPlugin plugin;
    private final List<Arena> arenas = new ArrayList<>();
    private final Set<String> reserved = ConcurrentHashMap.newKeySet();

    public ArenaManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        arenas.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("arenas");
        if (section == null) return;
        List<MapEntry> configured = new ArrayList<>();
        for (String key : section.getStringList("list")) {
            if (key != null) configured.add(new MapEntry(key));
        }
        for (MapEntry ignored : configured) { /* reserved for future compact list format */ }
        var list = plugin.getConfig().getMapList("arenas.list");
        for (var map : list) {
            String name = String.valueOf(map.getOrDefault("name", "Arena-" + arenas.size()));
            World world = Bukkit.getWorld(String.valueOf(map.get("world")));
            if (world == null) continue;
            Location a = location(world, map, "spawn-a");
            Location b = location(world, map, "spawn-b");
            if (a != null && b != null) arenas.add(new Arena(name, a, b));
        }
    }

    private Location location(World world, java.util.Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (!(raw instanceof java.util.Map<?, ?> m)) return null;
        return new Location(world,
                number(m.get("x")), number(m.get("y")), number(m.get("z")),
                (float) number(m.get("yaw")), (float) number(m.get("pitch")));
    }

    private double number(Object value) { return value instanceof Number n ? n.doubleValue() : 0.0; }

    public synchronized Arena acquire() {
        for (Arena arena : arenas) if (reserved.add(arena.name())) return arena;
        return null;
    }

    public void release(Arena arena) { if (arena != null) reserved.remove(arena.name()); }
    public int available() { return Math.max(0, arenas.size() - reserved.size()); }
    public List<Arena> all() { return List.copyOf(arenas); }

    private record MapEntry(String key) {}
}
