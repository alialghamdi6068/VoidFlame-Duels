package net.voidflame.duels;

import net.voidflame.core.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class StatsCommand implements CommandExecutor, TabCompleter {
    private final VoidFlameDuelsPlugin plugin;

    public StatsCommand(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidflame.stats")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            String category = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "elo";
            leaderboard(sender, category);
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /stats <player> or /stats top [elo|wins|losses|streak]");
                return true;
            }
            lookup(sender, player.getUniqueId(), player.getName());
            return true;
        }
        String target = args[0].trim();
        if (target.isEmpty() || target.length() > 16) {
            sender.sendMessage(ChatColor.RED + "Invalid player name.");
            return true;
        }
        Player online = Bukkit.getPlayerExact(target);
        UUID id = online != null ? online.getUniqueId() : Bukkit.getOfflinePlayer(target).getUniqueId();
        lookup(sender, id, target);
        return true;
    }

    private void lookup(CommandSender sender, UUID id, String name) {
        StorageService storage = storage();
        if (storage == null) {
            sender.sendMessage(ChatColor.RED + "Statistics storage is unavailable.");
            return;
        }
        storage.query("SELECT name, wins, losses, winstreak, best_winstreak, elo, rank " +
                        "FROM player_profiles WHERE uuid=? OR lower(name)=lower(?) LIMIT 1",
                id.toString(), name)
                .whenComplete((rows, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        sender.sendMessage(ChatColor.RED + "Could not load player statistics.");
                        plugin.getLogger().warning("Stats lookup failed: " + error.getMessage());
                        return;
                    }
                    if (rows == null || rows.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "No saved statistics found for " + name + ".");
                        return;
                    }
                    Map<String, Object> row = rows.getFirst();
                    sender.sendMessage(ChatColor.DARK_PURPLE + "§l" + row.getOrDefault("name", name) + " Statistics");
                    sender.sendMessage(ChatColor.GRAY + "Rank: " + ChatColor.LIGHT_PURPLE + row.getOrDefault("rank", "player"));
                    sender.sendMessage(ChatColor.GRAY + "ELO: " + ChatColor.AQUA + number(row.get("elo"), 1000));
                    sender.sendMessage(ChatColor.GRAY + "Wins: " + ChatColor.GREEN + number(row.get("wins"), 0)
                            + ChatColor.GRAY + " | Losses: " + ChatColor.RED + number(row.get("losses"), 0));
                    sender.sendMessage(ChatColor.GRAY + "Current streak: " + ChatColor.YELLOW + number(row.get("winstreak"), 0)
                            + ChatColor.GRAY + " | Best streak: " + ChatColor.GOLD + number(row.get("best_winstreak"), 0));
                }));
    }

    private void leaderboard(CommandSender sender, String category) {
        String column = switch (category) {
            case "wins" -> "wins";
            case "losses" -> "losses";
            case "streak" -> "winstreak";
            case "elo" -> "elo";
            default -> null;
        };
        if (column == null) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /stats top [elo|wins|losses|streak]");
            return;
        }
        StorageService storage = storage();
        if (storage == null) {
            sender.sendMessage(ChatColor.RED + "Statistics storage is unavailable.");
            return;
        }
        String sql = "SELECT name, wins, losses, winstreak, elo FROM player_profiles ORDER BY "
                + column + " DESC, wins DESC, name COLLATE NOCASE ASC LIMIT 10";
        storage.query(sql).whenComplete((rows, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                sender.sendMessage(ChatColor.RED + "Could not load the leaderboard.");
                plugin.getLogger().warning("Stats leaderboard failed: " + error.getMessage());
                return;
            }
            sender.sendMessage(ChatColor.DARK_PURPLE + "§lVoidFlame " + category.toUpperCase(Locale.ROOT) + " TOP 10");
            if (rows == null || rows.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "No player statistics have been recorded yet.");
                return;
            }
            int position = 1;
            for (Map<String, Object> row : rows) {
                Object value = row.get(column);
                sender.sendMessage(ChatColor.YELLOW + "#" + position++ + " "
                        + ChatColor.WHITE + row.getOrDefault("name", "Unknown")
                        + ChatColor.DARK_GRAY + " • "
                        + ChatColor.AQUA + number(value, 0));
            }
        }));
    }

    private StorageService storage() {
        RegisteredServiceProvider<StorageService> registration =
                Bukkit.getServicesManager().getRegistration(StorageService.class);
        return registration == null ? null : registration.getProvider();
    }

    private static String number(Object value, double fallback) {
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Double.isFinite(numeric)) {
                return numeric == Math.rint(numeric) ? Long.toString((long) numeric) : String.format(Locale.ROOT, "%.1f", numeric);
            }
        }
        return Double.toString(fallback);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> choices = new ArrayList<>(List.of("top"));
            choices.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("elo", "wins", "losses", "streak").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
