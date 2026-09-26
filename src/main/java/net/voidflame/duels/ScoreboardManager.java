package net.voidflame.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ScoreboardManager {
    private final VoidFlameDuelsPlugin plugin;
    private final org.bukkit.scoreboard.ScoreboardManager bukkit;
    private Method statsGetCached;
    private Object statsService;

    public ScoreboardManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        this.bukkit = Bukkit.getScoreboardManager();
        connectStats();
    }

    private void connectStats() {
        try {
            Class<?> type = Class.forName("net.voidflame.stats.StatsService");
            var registration = Bukkit.getServicesManager().getRegistration(type);
            if (registration != null) {
                statsService = registration.getProvider();
                statsGetCached = type.getMethod("getCached", UUID.class);
            }
        } catch (ReflectiveOperationException ignored) {
            statsService = null;
            statsGetCached = null;
        }
    }

    public void update(Player player) {
        if (bukkit == null) return;
        if (statsService == null) connectStats();

        Scoreboard board = bukkit.getNewScoreboard();
        Match match = plugin.matchManager().get(player.getUniqueId());
        String path = match != null ? "scoreboard.match" : "scoreboard.spawn";

        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) {
            player.setScoreboard(bukkit.getMainScoreboard());
            updateTab(player, match);
            return;
        }

        Objective objective = board.registerNewObjective("vf", org.bukkit.scoreboard.Criteria.DUMMY,
                color(plugin.getConfig().getString(path + ".title", "&5&lVOIDFLAME")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> configured = plugin.getConfig().getStringList(path + ".lines");
        int score = configured.size();
        Set<String> used = new HashSet<>();
        for (String line : configured) {
            String rendered = render(line, player, match);
            if (rendered.length() > 40) rendered = rendered.substring(0, 40);
            if (rendered.isBlank()) rendered = " ";
            while (!used.add(rendered)) rendered += ChatColor.RESET;
            objective.getScore(rendered).setScore(score--);
        }

        player.setScoreboard(board);
        updateTab(player, match);
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    private void updateTab(Player player, Match match) {
        String state = match == null ? "PRACTICE" : "IN DUEL";
        String header = color(plugin.getConfig().getString(
                "tab.header", "&5&lVOIDFLAME &8• &dPRACTICE NETWORK"));
        String footer = color(plugin.getConfig().getString(
                "tab.footer", "&7Status: &d%state% &8• &7Online: &f%server_online% &8• &5play.VoidFlame.net"))
                .replace("%state%", state)
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        player.sendPlayerListHeaderAndFooter(
                legacy.deserialize(header),
                legacy.deserialize(footer));
        player.playerListName(legacy.deserialize(color(player.getDisplayName())));
    }

    private String render(String line, Player player, Match match) {
        StatsView stats = stats(player.getUniqueId());
        String result = line
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%practice_in_match%", String.valueOf(plugin.matchManager().activeMatches() * 2))
                .replace("%practice_in_queue%", String.valueOf(plugin.queueManager().totalQueued()))
                .replace("%player_ping%", String.valueOf(player.getPing()))
                .replace("%opponent_ping%", match == null ? "-" : ping(match.opponent(player.getUniqueId())))
                .replace("%match_duration%", match == null ? "0:00" : formatDuration(match.durationSeconds()))
                .replace("%arena_name%", match == null ? "-" : match.arena().name())
                .replace("%kit_name%", match == null ? "-" : pretty(match.kit()))
                .replace("%opponent_name%", match == null ? "None" : name(match.opponent(player.getUniqueId())))
                .replace("%player_wins%", String.valueOf(stats.wins))
                .replace("%player_losses%", String.valueOf(stats.losses))
                .replace("%player_streak%", String.valueOf(stats.streak))
                .replace("%player_elo%", String.valueOf(Math.round(stats.elo)))
                .replace("%player_kills%", String.valueOf(stats.kills))
                .replace("%player_deaths%", String.valueOf(stats.deaths))
                .replace("%state%", match == null ? "Practice" : "Duel");
        return color(result);
    }

    private StatsView stats(UUID uuid) {
        if (statsService == null || statsGetCached == null) return StatsView.EMPTY;
        try {
            Object value = statsGetCached.invoke(statsService, uuid);
            Class<?> type = value.getClass();
            return new StatsView(
                    ((Number) type.getMethod("wins").invoke(value)).longValue(),
                    ((Number) type.getMethod("losses").invoke(value)).longValue(),
                    ((Number) type.getMethod("kills").invoke(value)).longValue(),
                    ((Number) type.getMethod("deaths").invoke(value)).longValue(),
                    ((Number) type.getMethod("streak").invoke(value)).longValue(),
                    ((Number) type.getMethod("elo").invoke(value)).doubleValue());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return StatsView.EMPTY;
        }
    }

    private record StatsView(long wins, long losses, long kills, long deaths, long streak, double elo) {
        static final StatsView EMPTY = new StatsView(0, 0, 0, 0, 0, 1000);
    }

    private String ping(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "-" : String.valueOf(player.getPing());
    }

    private String name(UUID uuid) {
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
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}