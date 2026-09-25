package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

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
        boolean inMatch = match != null;
        String path = inMatch ? "scoreboard.match" : "scoreboard.spawn";
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) {
            player.setScoreboard(bukkit.getMainScoreboard());
            return;
        }
        String title = color(plugin.getConfig().getString(path + ".title", "&b&lVOIDFLAME"));
        Objective objective = board.registerNewObjective("vf", org.bukkit.scoreboard.Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> configured = plugin.getConfig().getStringList(path + ".lines");
        int score = configured.size();
        for (String line : configured) {
            String rendered = render(line, player, match);
            if (rendered.length() > 40) rendered = rendered.substring(0, 40);
            if (rendered.isBlank()) rendered = " ";
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
                .replace("%match_duration%", match == null ? "0s" : formatDuration(match.durationSeconds()))
                .replace("%arena_name%", match == null ? "-" : match.arena().name())
                .replace("%kit_name%", match == null ? "-" : pretty(match.kit()))
                .replace("%opponent_name%", match == null ? "-" : name(match.opponent(player.getUniqueId())));
        return color(result);
    }

    private String name(java.util.UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "Opponent" : player.getName();
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
