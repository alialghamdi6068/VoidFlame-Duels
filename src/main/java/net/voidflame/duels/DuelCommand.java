package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DuelCommand implements CommandExecutor, TabCompleter {
    private final VoidFlameDuelsPlugin plugin;

    public DuelCommand(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "duels" -> { plugin.menu().open(p); yield true; }
            case "queue" -> queue(p, args);
            case "duel" -> duel(p, args);
            case "rematch" -> rematch(p, args);
            case "rejoin" -> rejoin(p);
            case "spectate" -> spectate(p, args);
            case "kiteditor" -> kitEditor(p, args);
            default -> true;
        };
    }

    private boolean queue(Player p, String[] args) {
        if (args.length == 0) { plugin.menu().open(p); return true; }
        if (args[0].equalsIgnoreCase("leave")) {
            p.sendMessage(plugin.queueManager().leave(p) ? plugin.message("left-queue") : plugin.message("not-queued"));
            plugin.scoreboardManager().update(p);
            return true;
        }
        KitType kit = parseKit(args[0]);
        if (kit == null) { p.sendMessage(plugin.message("unknown-kit")); return true; }
        if (plugin.partyManager().partyOf(p.getUniqueId()) != null) {
            p.sendMessage(plugin.message("party-cannot-queue"));
            return true;
        }
        boolean ok = plugin.queueManager().join(p, kit);
        p.sendMessage(ok ? plugin.message("joined-queue").replace("<kit>", pretty(kit)) : plugin.message("already-queued"));
        plugin.scoreboardManager().update(p);
        return true;
    }

    private boolean duel(Player p, String[] args) {
        if (!plugin.playerSettings().duelRequests(p)) { p.sendMessage(plugin.message("duel-unavailable")); return true; }
        if (args.length >= 2 && args[0].equalsIgnoreCase("accept")) {
            Player sender = Bukkit.getPlayerExact(args[1]);
            if (sender == null) { p.sendMessage(plugin.message("player-not-found")); return true; }
            DuelRequestManager.Request request = plugin.requests().getFrom(p, sender);
            if (request == null) { p.sendMessage(plugin.message("request-expired")); return true; }
            if (!plugin.matchManager().startDirect(p, sender, request.kit())) {
                p.sendMessage(plugin.message("duel-start-failed"));
                return true;
            }
            plugin.requests().remove(p);
            p.sendMessage(plugin.message("request-accepted"));
            return true;
        }
        if (args.length < 1) { p.sendMessage(ChatColor.YELLOW + "/duel <player> [kit]"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null && !plugin.playerSettings().duelRequests(target)) { p.sendMessage(plugin.message("duel-unavailable")); return true; }
        if (target == null) { p.sendMessage(plugin.message("player-not-found")); return true; }
        KitType kit = args.length >= 2 ? parseKit(args[1]) : KitType.SWORD;
        if (kit == null) { p.sendMessage(plugin.message("unknown-kit")); return true; }
        if (!plugin.requests().send(p, target, kit)) {
            p.sendMessage(plugin.message("duel-unavailable"));
            return true;
        }
        p.sendMessage(plugin.message("duel-sent").replace("<player>", target.getName()));
        target.sendMessage(plugin.message("duel-received").replace("<player>", p.getName()).replace("<kit>", pretty(kit)));
        return true;
    }

    private boolean rematch(Player p, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("accept")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { p.sendMessage(plugin.message("player-not-found")); return true; }
            p.sendMessage(plugin.rematches().accept(p, target) ? plugin.message("request-accepted") : plugin.message("request-expired"));
            return true;
        }
        p.sendMessage(plugin.rematches().send(p) ? plugin.message("rematch-sent") : plugin.message("request-expired"));
        return true;
    }

    private boolean rejoin(Player p) {
        p.sendMessage(plugin.matchManager().rejoin(p) ? plugin.message("rejoined") : plugin.message("no-rejoin"));
        return true;
    }

    private boolean spectate(Player p, String[] args) {
        if (args.length < 1) { p.sendMessage(ChatColor.YELLOW + "/spectate <player>"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage(plugin.message("player-not-found")); return true; }
        if (!plugin.spectatorManager().spectate(p, target)) p.sendMessage(plugin.message("spectate-failed"));
        return true;
    }

    private boolean kitEditor(Player p, String[] args) {
        if (args.length < 1) {
            p.sendMessage(ChatColor.YELLOW + "/kiteditor <kit>");
            return true;
        }
        KitType kit = parseKit(args[0]);
        if (kit == null) {
            p.sendMessage(plugin.message("unknown-kit"));
            return true;
        }
        if (!plugin.kitEditorManager().open(p, kit)) {
            p.sendMessage(plugin.message("kit-editor-failed"));
        }
        return true;
    }

    private KitType parseKit(String input) {
        try { return KitType.fromConfig(input); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String pretty(KitType k) { return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_', ' '); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ((name.equals("queue") || name.equals("duel") || name.equals("kiteditor")) && args.length == 1) {
            if (name.equals("duel")) out.add("accept");
            for (KitType k : KitType.values()) out.add(pretty(k).toLowerCase(Locale.ROOT));
            if (name.equals("duel")) Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).forEach(out::add);
        } else if ((name.equals("duel") || name.equals("rematch")) && args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).forEach(out::add);
        } else if (name.equals("spectate") && args.length == 1) {
            Bukkit.getOnlinePlayers().stream().filter(x -> plugin.matchManager().isInMatch(x.getUniqueId())).map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).forEach(out::add);
        }
        return out.stream().distinct().limit(50).toList();
    }
}
