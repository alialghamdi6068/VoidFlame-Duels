package net.voidflame.duels;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.voidflame.core.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cached VoidFlame stats for TAB / PlaceholderAPI; no synchronous database queries. */
public final class VoidFlamePlaceholders extends PlaceholderExpansion implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();

    private record Profile(int wins, int losses, int streak, int bestStreak, int elo, long coins, String rank) {
        static Profile empty() { return new Profile(0, 0, 0, 0, 1000, 0, "Player"); }
    }

    public VoidFlamePlaceholders(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "voidflame"; }
    @Override public String getAuthor() { return "VoidFlame"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    public void start() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        register();
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        }, 100L, 100L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) refresh(event.getPlayer());
        }, 20L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        profiles.remove(event.getPlayer().getUniqueId());
    }

    private void refresh(Player player) {
        var registration = Bukkit.getServicesManager().getRegistration(StorageService.class);
        if (registration == null || registration.getProvider() == null) {
            profiles.putIfAbsent(player.getUniqueId(), Profile.empty());
            return;
        }
        UUID uuid = player.getUniqueId();
        registration.getProvider().query(
                "SELECT wins, losses, winstreak, best_winstreak, elo, coins, rank FROM player_profiles WHERE uuid = ? LIMIT 1",
                uuid.toString()
        ).whenComplete((rows, error) -> {
            if (error != null || rows == null || rows.isEmpty()) {
                profiles.putIfAbsent(uuid, Profile.empty());
                return;
            }
            Map<String, Object> row = rows.get(0);
            Profile profile = new Profile(number(row.get("wins")), number(row.get("losses")),
                    number(row.get("winstreak")), number(row.get("best_winstreak")),
                    number(row.get("elo"), 1000), longNumber(row.get("coins")),
                    row.get("rank") == null ? "Player" : String.valueOf(row.get("rank")));
            profiles.put(uuid, profile);
        });
    }

    @Override public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null) return "";
        Profile p = profiles.getOrDefault(player.getUniqueId(), Profile.empty());
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "wins" -> String.valueOf(p.wins());
            case "losses" -> String.valueOf(p.losses());
            case "winstreak", "streak" -> String.valueOf(p.streak());
            case "beststreak" -> String.valueOf(p.bestStreak());
            case "elo" -> String.valueOf(p.elo());
            case "coins" -> String.valueOf(p.coins());
            case "rank" -> p.rank();
            default -> null;
        };
    }

    private int number(Object value) { return number(value, 0); }
    private int number(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private long longNumber(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
