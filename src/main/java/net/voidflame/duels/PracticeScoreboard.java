package net.voidflame.duels;

import net.voidflame.core.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VoidFlame's lightweight Practice sidebar. Player stats are read through
 * VoidFlame-Core and cached; the scoreboard renderer never performs SQL work.
 */
public final class PracticeScoreboard implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private int refreshTick;

    private record Profile(String name, String rank, int wins, int losses, int streak, int elo, long coins) {
        static Profile empty(String name) {
            return new Profile(name, "Player", 0, 0, 0, 1000, 0);
        }
    }

    public PracticeScoreboard(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadProfile(player);
            render(player);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            refreshTick += 20;
            for (Player player : Bukkit.getOnlinePlayers()) {
                render(player);
                if (refreshTick >= 100) loadProfile(player);
            }
            if (refreshTick >= 100) refreshTick = 0;
        }, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        profiles.putIfAbsent(player.getUniqueId(), Profile.empty(player.getName()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                loadProfile(player);
                render(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profiles.remove(event.getPlayer().getUniqueId());
    }

    private void loadProfile(Player player) {
        var registration = Bukkit.getServicesManager().getRegistration(StorageService.class);
        if (registration == null || registration.getProvider() == null) {
            profiles.putIfAbsent(player.getUniqueId(), Profile.empty(player.getName()));
            return;
        }
        UUID uuid = player.getUniqueId();
        String currentName = player.getName();
        registration.getProvider().query(
                "SELECT name, rank, wins, losses, winstreak, elo, coins FROM player_profiles WHERE uuid = ? LIMIT 1",
                uuid.toString()
        ).whenComplete((rows, error) -> {
            if (error != null) {
                plugin.getLogger().fine("Scoreboard profile lookup failed for " + currentName + ": " + error.getMessage());
                return;
            }
            Profile profile = Profile.empty(currentName);
            if (rows != null && !rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                profile = new Profile(
                        string(row.get("name"), currentName),
                        string(row.get("rank"), "Player"),
                        number(row.get("wins")),
                        number(row.get("losses")),
                        number(row.get("winstreak")),
                        number(row.get("elo"), 1000),
                        longNumber(row.get("coins"))
                );
            }
            Profile result = profile;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    profiles.put(uuid, result);
                    render(online);
                }
            });
        });
    }

    private void render(Player player) {
        if (!player.isOnline()) return;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("vfpractice", "dummy", ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "VOIDFLAME");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Profile profile = profiles.getOrDefault(player.getUniqueId(), Profile.empty(player.getName()));
        boolean inMatch = plugin.matchManager() != null && plugin.matchManager().isInMatch(player.getUniqueId());
        List<String> lines = List.of(
                "§8§m----------------",
                "§7Mode: " + (inMatch ? "§cIn Duel" : "§aPractice"),
                "§dPlayer §8» §f" + trim(profile.name(), 12),
                "§7Rank §8» " + rankColor(profile.rank()) + trim(profile.rank(), 10),
                "§r ",
                "§aWins §8» §f" + profile.wins(),
                "§cLosses §8» §f" + profile.losses(),
                "§bStreak §8» §f" + profile.streak(),
                "§eELO §8» §f" + profile.elo(),
                "§6Coins §8» §f" + profile.coins(),
                "§r  ",
                "§5play.VoidFlame.net"
        );
        int score = lines.size();
        for (String line : lines) {
            String unique = line + ChatColor.values()[score % ChatColor.values().length];
            objective.getScore(unique).setScore(score--);
        }
        player.setScoreboard(board);
    }

    private String rankColor(String rank) {
        return switch (rank.toLowerCase(java.util.Locale.ROOT)) {
            case "owner" -> "§5";
            case "manager" -> "§6";
            case "developer" -> "§b";
            case "admin" -> "§c";
            case "moderator" -> "§d";
            case "helper" -> "§a";
            case "mvp" -> "§e";
            case "vip" -> "§3";
            default -> "§7";
        };
    }

    private String trim(String value, int max) {
        if (value == null) return "Player";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String string(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private int number(Object value) {
        return number(value, 0);
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private long longNumber(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
