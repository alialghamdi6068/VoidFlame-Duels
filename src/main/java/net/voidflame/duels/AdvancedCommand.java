package net.voidflame.duels;

import org.bukkit.Bukkit;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
            case "reports" -> { if (!JavaAdminAccess.hasAdminAccess(player)) { player.sendMessage(color("&cJava staff only.")); yield true; } if (args.length >= 2 && List.of("claim", "resolve", "reopen").contains(args[0].toLowerCase(Locale.ROOT))) plugin.advancedFeatures().updateReportStatus(player, args[1], switch (args[0].toLowerCase(Locale.ROOT)) { case "claim" -> "CLAIMED"; case "resolve" -> "RESOLVED"; default -> "OPEN"; }); else plugin.advancedFeatures().openReports(player); yield true; }
            case "history" -> { plugin.advancedFeatures().openHistory(player); yield true; }
            case "coinshop" -> { if (args.length >= 2 && args[0].equalsIgnoreCase("buy")) plugin.advancedFeatures().purchaseShopProduct(player, args[1]); else plugin.advancedFeatures().openCoinShop(player); yield true; }
            case "practice", "totalpractice" -> { if (args.length >= 2 && args[0].equalsIgnoreCase("start")) plugin.advancedFeatures().startTrainingCommand(player, args[1]); else plugin.advancedFeatures().openPractice(player); yield true; }
            case "settings" -> { plugin.settingsMenu().open(player); yield true; }
            case "goldenhard" -> { plugin.advancedFeatures().toggleGoldenHard(player); yield true; }
            case "ffa" -> ffa(player, args);
            case "coins" -> { if (!JavaAdminAccess.hasAdminAccess(player)) { player.sendMessage(color("&cJava staff only.")); yield true; } yield coins(sender, args); }
            case "replay" -> replay(player, args);
            default -> true;
        };
    }

    private boolean replay(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("features.replay.enabled", true)) {
            player.sendMessage(color("&cReplay system is disabled."));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "/replay <match-id>");
            return true;
        }
        java.util.UUID matchId;
        try { matchId = java.util.UUID.fromString(args[0]); }
        catch (IllegalArgumentException ex) {
            player.sendMessage(color("&cInvalid match ID."));
            return true;
        }
        plugin.advancedFeatures().loadReplay(player.getUniqueId(), matchId, raw -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (raw == null) {
                player.sendMessage(color("&cReplay not found or you do not have access."));
                return;
            }
            String summary = raw;
            int max = Math.max(1, plugin.getConfig().getInt("features.replay.max-events-per-match", 2000));
            String[] parts = raw.split("\\|", -1);
            player.sendMessage(color("&8&m--------------------"));
            player.sendMessage(color("&bVoidFlame &fReplay &7" + matchId));
            for (String part : parts) {
                if (part.startsWith("kit=") || part.startsWith("arena=") || part.startsWith("duration=")) {
                    player.sendMessage(color("&7" + part));
                } else if (part.startsWith("events=")) {
                    int count = part.length() <= 7 || part.substring(7).isBlank() ? 0 : part.substring(7).split(";", -1).length;
                    player.sendMessage(color("&7Events: &f" + Math.min(count, max)));
                }
            }
            player.sendMessage(color("&8&m--------------------"));
        }));
        return true;
    }

    private boolean ffa(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            player.sendMessage(plugin.ffaManager().leave(player) ? color("&aYou left FFA.") : color("&cYou are not in FFA."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stats")) {
            plugin.ffaManager().sendStats(player);
            return true;
        }
        String kit = args.length == 0 ? "sword" : args[0];
        plugin.ffaManager().join(player, kit);
        return true;
    }

    private boolean report(Player reporter, String[] args) {
        if (args.length < 2) {
            openReportTargets(reporter);
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


    private void openReportTargets(Player viewer) {
        List<DialogBody> body = List.of(
                DialogBody.item(new ItemStack(Material.WRITABLE_BOOK),
                        DialogBody.plainMessage(Component.text("Report a player to the VoidFlame staff team.", NamedTextColor.GRAY), 240),
                        true, true, 48, 48),
                DialogBody.plainMessage(Component.text("Enter the exact online player name and a short reason.", NamedTextColor.GRAY), 300)
        );
        List<ActionButton> actions = List.of(
                ActionButton.create(Component.text("Submit Report", NamedTextColor.RED),
                        Component.text("Send the report to authorized staff.", NamedTextColor.GRAY), 180,
                        DialogAction.commandTemplate("/report $(target) $(reason)")),
                ActionButton.create(Component.text("Cancel", NamedTextColor.GRAY),
                        Component.text("Close without submitting.", NamedTextColor.GRAY), 180,
                        DialogAction.commandTemplate("/vfmenu"))
        );
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("VoidFlame • Report Player", NamedTextColor.WHITE))
                        .externalTitle(Component.text("VoidFlame", NamedTextColor.LIGHT_PURPLE))
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text("target", Component.text("Player name", NamedTextColor.GRAY))
                                        .initial("").maxLength(16).width(240).build(),
                                DialogInput.text("reason", Component.text("Reason", NamedTextColor.GRAY))
                                        .initial("").maxLength(200).width(300).build()
                        ))
                        .build())
                .type(DialogType.multiAction(actions).columns(2).build()));
        viewer.showDialog(dialog);
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
