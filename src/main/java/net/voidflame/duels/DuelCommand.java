package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class DuelCommand implements CommandExecutor, TabCompleter {
    private final VoidFlameDuelsPlugin plugin;
    public DuelCommand(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (command.getName().equalsIgnoreCase("duels")) { plugin.menu().open(p); return true; }
        if (command.getName().equalsIgnoreCase("queue")) return queue(p, args);
        if (command.getName().equalsIgnoreCase("duel")) return duel(p, args);
        if (command.getName().equalsIgnoreCase("rematch")) return rematch(p, args);
        if (command.getName().equalsIgnoreCase("rejoin")) return rejoin(p);
        return true;
    }

    private boolean queue(Player p, String[] args) {
        if (args.length == 0) { plugin.menu().open(p); return true; }
        if (args[0].equalsIgnoreCase("leave")) {
            p.sendMessage(plugin.queueManager().leave(p) ? plugin.message("left-queue") : plugin.message("not-queued"));
            return true;
        }
        try {
            KitType kit = KitType.fromConfig(args[0]);
            boolean ok = plugin.queueManager().join(p, kit);
            p.sendMessage(ok ? plugin.message("joined-queue").replace("<kit>", pretty(kit)) : plugin.message("already-queued"));
        } catch (IllegalArgumentException e) { p.sendMessage(ChatColor.RED + "Unknown kit."); }
        return true;
    }

    private boolean duel(Player p, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("accept") && args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { p.sendMessage(plugin.message("player-not-found")); return true; }
            var request = plugin.requests().getFrom(p, target);
            if (request == null) { p.sendMessage(plugin.message("request-expired")); return true; }
            plugin.requests().remove(p);
            plugin.matchManager().startDirect(p, target, request.kit());
            p.sendMessage(plugin.message("request-accepted"));
            return true;
        }
        if (args.length < 1) { p.sendMessage(ChatColor.YELLOW + "/duel <player> [kit]"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage(plugin.message("player-not-found")); return true; }
        KitType kit = args.length >= 2 ? KitType.fromConfig(args[1]) : KitType.SWORD;
        if (!plugin.requests().send(p, target, kit)) { p.sendMessage(plugin.message("already-in-match")); return true; }
        p.sendMessage(plugin.message("duel-sent").replace("<player>", target.getName()));
        target.sendMessage(plugin.message("duel-received").replace("<player>", p.getName()).replace("<kit>", pretty(kit)));
        return true;
    }

    private boolean rematch(Player p, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("accept")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { p.sendMessage(plugin.message("player-not-found")); return true; }
            if (!plugin.rematches().accept(p, target)) p.sendMessage(plugin.message("request-expired"));
            return true;
        }
        if (!plugin.rematches().send(p)) p.sendMessage(plugin.message("request-expired"));
        else p.sendMessage(plugin.message("rematch-sent"));
        return true;
    }

    private boolean rejoin(Player p) {
        if (!plugin.matchManager().rejoin(p)) p.sendMessage(plugin.message("no-rejoin"));
        return true;
    }

    private String pretty(KitType k) { return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_', ' '); }

    @Override public java.util.List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1 && (c.getName().equalsIgnoreCase("queue") || c.getName().equalsIgnoreCase("duel")))
            return java.util.Arrays.stream(KitType.values()).map(k -> k.name().toLowerCase(Locale.ROOT)).toList();
        return java.util.List.of();
    }
}
