package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PartyCommand implements CommandExecutor, TabCompleter {
    private final VoidFlameDuelsPlugin plugin;

    public PartyCommand(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (plugin.partyManager().create(player)) {
                    player.sendMessage(plugin.message("party-created"));
                } else {
                    player.sendMessage(plugin.message("party-already-in"));
                }
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "/party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.message("player-not-found"));
                    return true;
                }
                if (plugin.partyManager().invite(player, target)) {
                    player.sendMessage(plugin.message("party-invite-sent").replace("<player>", target.getName()));
                    target.sendMessage(plugin.message("party-invite-received").replace("<player>", player.getName()));
                } else {
                    player.sendMessage(plugin.message("party-invite-failed"));
                }
            }
            case "accept" -> {
                if (plugin.partyManager().accept(player)) {
                    player.sendMessage(plugin.message("party-joined"));
                } else {
                    player.sendMessage(plugin.message("party-no-invite"));
                }
            }
            case "leave" -> {
                if (plugin.partyManager().leave(player)) {
                    player.sendMessage(plugin.message("party-left"));
                } else {
                    player.sendMessage(plugin.message("party-not-in"));
                }
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "/party kick <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.message("player-not-found"));
                    return true;
                }
                player.sendMessage(plugin.partyManager().kick(player, target)
                        ? plugin.message("party-kicked").replace("<player>", target.getName())
                        : plugin.message("party-action-failed"));
            }
            case "disband" -> {
                player.sendMessage(plugin.partyManager().disband(player)
                        ? plugin.message("party-disbanded")
                        : plugin.message("party-action-failed"));
            }
            case "info", "list" -> {
                PartyManager.Party party = plugin.partyManager().partyOf(player.getUniqueId());
                if (party == null) {
                    player.sendMessage(plugin.message("party-not-in"));
                    return true;
                }
                String members = party.members().stream()
                        .map(id -> {
                            Player p = Bukkit.getPlayer(id);
                            return p == null ? Bukkit.getOfflinePlayer(id).getName() : p.getName();
                        })
                        .filter(java.util.Objects::nonNull)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("-");
                player.sendMessage(plugin.message("party-info")
                        .replace("<leader>", name(party.leader()))
                        .replace("<members>", members));
            }
            default -> showHelp(player);
        }
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "/party create");
        player.sendMessage(ChatColor.YELLOW + "/party invite <player>");
        player.sendMessage(ChatColor.YELLOW + "/party accept");
        player.sendMessage(ChatColor.YELLOW + "/party leave");
        player.sendMessage(ChatColor.YELLOW + "/party kick <player>");
        player.sendMessage(ChatColor.YELLOW + "/party disband");
        player.sendMessage(ChatColor.YELLOW + "/party info");
    }

    private String name(java.util.UUID id) {
        Player p = Bukkit.getPlayer(id);
        String name = p == null ? Bukkit.getOfflinePlayer(id).getName() : p.getName();
        return name == null ? id.toString() : name;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(List.of("create", "invite", "accept", "leave", "kick", "disband", "info"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .forEach(result::add);
        }
        return result.stream().distinct().toList();
    }
}
