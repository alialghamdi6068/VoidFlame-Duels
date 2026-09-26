package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdvancedCommand implements CommandExecutor, TabCompleter {
    private final VoidFlameDuelsPlugin plugin;

    public AdvancedCommand(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        return switch (name) {
            case "report" -> report(player, args);
            case "coinshop" -> { plugin.advancedFeatures().openCoinShop(player); yield true; }
            case "practice", "totalpractice" -> { plugin.advancedFeatures().openPractice(player); yield true; }
            case "goldenhard" -> { plugin.advancedFeatures().toggleGoldenHard(player); yield true; }
            case "coins" -> coins(sender, args);
            default -> true;
        };
    }

    private boolean report(Player reporter, String[] args) {
        if (args.length < 2) {
            reporter.sendMessage(ChatColor.YELLOW + "/report <player> <reason>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            reporter.sendMessage(plugin.message("player-not-found"));
            return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        reporter.sendMessage(plugin.advancedFeatures().report(reporter, target, reason)
                ? color("&aReport sent to online staff.")
                : color("&cYour report could not be sent right now."));
        return true;
    }

    private boolean coins(CommandSender sender, String[] args) {
        if (!sender.hasPermission("voidflame.duels.coins.admin")) {
            sender.sendMessage(plugin.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/coins <player> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.message("player-not-found"));
            return true;
        }
        int amount;
        try { amount = Integer.parseInt(args[1]); }
        catch (NumberFormatException ex) {
            sender.sendMessage(color("&cAmount must be a number."));
            return true;
        }
        plugin.advancedFeatures().addCoins(target.getUniqueId(), amount);
        sender.sendMessage(color("&aUpdated coins for &e" + target.getName() + "&a."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("report") && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .limit(50).toList();
        }
        if (command.getName().equalsIgnoreCase("coins") && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .limit(50).toList();
        }
        return new ArrayList<>();
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
