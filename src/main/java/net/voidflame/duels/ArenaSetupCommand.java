package net.voidflame.duels;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ArenaSetupCommand implements CommandExecutor, TabCompleter {
    private final VoidFlameDuelsPlugin plugin;

    public ArenaSetupCommand(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Arena setup must be performed in-game.");
            return true;
        }
        if (!JavaAdminAccess.hasAdminAccess(player)) {
            player.sendMessage(ChatColor.RED + "Java staff only.");
            return true;
        }
        if (args.length == 0) {
            usage(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> list(player);
            case "create" -> create(player, args);
            case "setspawn" -> setSpawn(player, args);
            case "pos1" -> setCorner(player, args, true);
            case "pos2" -> setCorner(player, args, false);
            case "region" -> saveRegion(player, args);
            case "remove" -> remove(player, args);
            default -> usage(player);
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length != 2 || !validName(args[1])) {
            player.sendMessage(ChatColor.RED + "Usage: /duelarena create <name>");
            return;
        }
        String name = args[1];
        List<Map<String, Object>> arenas = arenas();
        if (find(arenas, name) >= 0) {
            player.sendMessage(ChatColor.RED + "An arena with that name already exists.");
            return;
        }
        Map<String, Object> arena = new LinkedHashMap<>();
        arena.put("name", name);
        arena.put("world", player.getWorld().getName());
        arena.put("spawn-a", locationMap(player.getLocation()));
        arena.put("spawn-b", locationMap(player.getLocation()));
        arenas.add(arena);
        plugin.getConfig().set("arenas.list", arenas);
        plugin.saveConfig();
        player.sendMessage(ChatColor.GREEN + "Arena '" + name + "' created in world '" + player.getWorld().getName() + "'. Set both spawns and its reset region before using it.");
    }

    private void setSpawn(Player player, String[] args) {
        if (args.length != 3 || !(args[2].equalsIgnoreCase("a") || args[2].equalsIgnoreCase("b"))) {
            player.sendMessage(ChatColor.RED + "Usage: /duelarena setspawn <name> <a|b>");
            return;
        }
        List<Map<String, Object>> arenas = arenas();
        int index = find(arenas, args[1]);
        if (index < 0) {
            player.sendMessage(ChatColor.RED + "Arena not found.");
            return;
        }
        Map<String, Object> arena = arenas.get(index);
        if (!player.getWorld().getName().equals(String.valueOf(arena.get("world")))) {
            player.sendMessage(ChatColor.RED + "You must be in arena world '" + arena.get("world") + "'.");
            return;
        }
        arena.put(args[2].equalsIgnoreCase("a") ? "spawn-a" : "spawn-b", locationMap(player.getLocation()));
        save(arenas);
        player.sendMessage(ChatColor.GREEN + "Spawn " + args[2].toUpperCase(Locale.ROOT) + " saved for arena '" + args[1] + "'.");
    }

    private void setCorner(Player player, String[] args, boolean first) {
        if (args.length != 2 || find(arenas(), args[1]) < 0) {
            player.sendMessage(ChatColor.RED + "Usage: /duelarena " + (first ? "pos1" : "pos2") + " <name>");
            return;
        }
        Map<String, Object> point = locationMap(player.getLocation());
        plugin.getConfig().set("arenas.setup." + args[1] + (first ? ".pos1" : ".pos2"), point);
        plugin.saveConfig();
        player.sendMessage(ChatColor.GREEN + (first ? "First" : "Second") + " region corner saved for '" + args[1] + "'.");
    }

    private void saveRegion(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Usage: /duelarena region <name>");
            return;
        }
        List<Map<String, Object>> arenas = arenas();
        int index = find(arenas, args[1]);
        if (index < 0) {
            player.sendMessage(ChatColor.RED + "Arena not found.");
            return;
        }
        Map<String, Object> arena = arenas.get(index);
        String worldName = String.valueOf(arena.get("world"));
        if (!player.getWorld().getName().equals(worldName)) {
            player.sendMessage(ChatColor.RED + "You must be in arena world '" + worldName + "'.");
            return;
        }
        Map<String, Object> p1 = plugin.getConfig().getConfigurationSection("arenas.setup." + args[1]) == null
                ? null : plugin.getConfig().getConfigurationSection("arenas.setup." + args[1]).getConfigurationSection("pos1") == null
                ? null : (Map<String, Object>) plugin.getConfig().getConfigurationSection("arenas.setup." + args[1] + ".pos1").getValues(false);
        Map<String, Object> p2 = plugin.getConfig().getConfigurationSection("arenas.setup." + args[1]) == null
                ? null : plugin.getConfig().getConfigurationSection("arenas.setup." + args[1]).getConfigurationSection("pos2") == null
                ? null : (Map<String, Object>) plugin.getConfig().getConfigurationSection("arenas.setup." + args[1] + ".pos2").getValues(false);
        if (p1 == null || p2 == null) {
            player.sendMessage(ChatColor.RED + "Set both corners first with /duelarena pos1 and /duelarena pos2.");
            return;
        }
        if (!worldName.equals(String.valueOf(p1.get("world"))) || !worldName.equals(String.valueOf(p2.get("world")))) {
            player.sendMessage(ChatColor.RED + "Both region corners must be in the arena's configured world.");
            return;
        }
        World world = player.getWorld();
        int x1 = number(p1.get("x")), y1 = number(p1.get("y")), z1 = number(p1.get("z"));
        int x2 = number(p2.get("x")), y2 = number(p2.get("y")), z2 = number(p2.get("z"));
        int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight()) {
            player.sendMessage(ChatColor.RED + "Region height is outside the world's build limits.");
            return;
        }
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        long limit = Math.max(1L, plugin.getConfig().getLong("arenas.max-reset-blocks", 100_000L));
        if (volume <= 0 || volume > limit) {
            player.sendMessage(ChatColor.RED + "Region is too large (" + volume + " blocks). Maximum: " + limit + ".");
            return;
        }
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("min", Map.of("x", minX, "y", minY, "z", minZ));
        region.put("max", Map.of("x", maxX, "y", maxY, "z", maxZ));
        arena.put("region", region);
        save(arenas);
        player.sendMessage(ChatColor.GREEN + "Reset region saved for '" + args[1] + "' (" + volume + " blocks).");
    }

    private void remove(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Usage: /duelarena remove <name>");
            return;
        }
        List<Map<String, Object>> arenas = arenas();
        int index = find(arenas, args[1]);
        if (index < 0) {
            player.sendMessage(ChatColor.RED + "Arena not found.");
            return;
        }
        arenas.remove(index);
        save(arenas);
        plugin.getConfig().set("arenas.setup." + args[1], null);
        plugin.saveConfig();
        player.sendMessage(ChatColor.YELLOW + "Arena '" + args[1] + "' removed from configuration.");
    }

    private void list(Player player) {
        List<Map<String, Object>> arenas = arenas();
        if (arenas.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No arenas configured. Use /duelarena create <name>.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Configured arenas:");
        for (Map<String, Object> arena : arenas) {
            boolean ready = arena.containsKey("region") && arena.containsKey("spawn-a") && arena.containsKey("spawn-b");
            player.sendMessage((ready ? ChatColor.GREEN : ChatColor.RED) + " • " + arena.get("name")
                    + ChatColor.GRAY + " — " + arena.get("world") + (ready ? " (configured)" : " (needs setup)"));
        }
    }

    private List<Map<String, Object>> arenas() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("arenas.list")) {
            Map<String, Object> item = new LinkedHashMap<>();
            raw.forEach((key, value) -> item.put(String.valueOf(key), value));
            result.add(item);
        }
        return result;
    }

    private int find(List<Map<String, Object>> arenas, String name) {
        for (int i = 0; i < arenas.size(); i++) {
            if (name.equalsIgnoreCase(String.valueOf(arenas.get(i).get("name")))) return i;
        }
        return -1;
    }

    private void save(List<Map<String, Object>> arenas) {
        plugin.getConfig().set("arenas.list", arenas);
        plugin.saveConfig();
    }

    private static Map<String, Object> locationMap(Location location) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    private static void usage(Player player) {
        player.sendMessage(ChatColor.AQUA + "VoidFlame Arena Setup");
        player.sendMessage(ChatColor.GRAY + "/duelarena list");
        player.sendMessage(ChatColor.GRAY + "/duelarena create <name>");
        player.sendMessage(ChatColor.GRAY + "/duelarena setspawn <name> <a|b>");
        player.sendMessage(ChatColor.GRAY + "/duelarena pos1 <name>  — stand at first region corner");
        player.sendMessage(ChatColor.GRAY + "/duelarena pos2 <name>  — stand at opposite region corner");
        player.sendMessage(ChatColor.GRAY + "/duelarena region <name>");
        player.sendMessage(ChatColor.GRAY + "/duelarena remove <name>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "create", "setspawn", "pos1", "pos2", "region", "remove");
        if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            return arenas().stream().map(a -> String.valueOf(a.get("name"))).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setspawn")) return List.of("a", "b");
        return List.of();
    }
}
