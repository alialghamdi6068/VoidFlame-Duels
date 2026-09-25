package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ScoreboardManager {
    private final VoidFlameDuelsPlugin plugin;
    private final org.bukkit.scoreboard.ScoreboardManager bukkit;

    public ScoreboardManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        this.bukkit = Bukkit.getScoreboardManager();
    }

    public void update(Player player) {
        if (bukkit == null) return;
        Scoreboard board = bukkit.getNewScoreboard();
        Match match = plugin.matchManager().get(player.getUniqueId());
        String path = match != null ? "scoreboard.match" : "scoreboard.spawn";
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) {
            player.setScoreboard(bukkit.getMainScoreboard());
            return;
        }
        Objective objective = board.registerNewObjective("vf", org.bukkit.scoreboard.Criteria.DUMMY,
                color(plugin.getConfig().getString(path + ".title", "&b&lVOIDFLAME")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> configured = plugin.getConfig().getStringList(path + ".lines");
        int score = configured.size();
        Set<String> used = new HashSet<>();
        for (String line : configured) {
            String rendered = render(line, player, match);
            if (rendered.length() > 40) rendered = rendered.substring(0, 40);
            if (rendered.isBlank()) rendered = " ";
            while (!used.add(rendered)) rendered = rendered + ChatColor.RESET;
            objective.getScore(rendered).setScore(score--);
        }
        player.setScoreboard(board);
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    private String render(String line, Player player, Match match) {
        String result = line
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%practice_in_match%", String.valueOf(plugin.matchManager().activeMatches() * 2))
                .replace("%practice_in_queue%", String.valueOf(plugin.queueManager().totalQueued()))
                .replace("%player_ping%", String.valueOf(player.getPing()))
                .replace("%opponent_ping%", match == null ? "-" : ping(match.opponent(player.getUniqueId())))
                .replace("%match_duration%", match == null ? "0s" : formatDuration(match.durationSeconds()))
                .replace("%arena_name%", match == null ? "-" : match.arena().name())
                .replace("%kit_name%", match == null ? "-" : pretty(match.kit()))
                .replace("%opponent_name%", match == null ? "-" : name(match.opponent(player.getUniqueId())))
                .replace("%vault_prefix%", prefix(player));
        return color(result);
    }

    private String ping(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "-" : String.valueOf(player.getPing());
    }

    private String name(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "Opponent" : player.getName();
    }

    private String prefix(Player player) {
        var pluginManager = Bukkit.getPluginManager();
        if (pluginManager.isPluginEnabled("Vault")) {
            try {
                Class<?> rsp = Class.forName("net.milkbowl.vault.chat.Chat");
                Object registration = Bukkit.getServicesManager().getRegistration((Class) rsp);
                if (registration != null) {
                    Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
                    Object value = provider.getClass().getMethod("getPlayerPrefix", String.class, String.class)
                            .invoke(provider, player.getWorld().getName(), player.getName());
                    return value == null ? "" : String.valueOf(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Vault is optional.
            }
        }
        return "";
    }

    private String formatDuration(long seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    private String pretty(KitType kit) {
        return kit == KitType.SPEAR_MACE ? "Spear & Mace" : kit.name().replace('_', ' ');
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
