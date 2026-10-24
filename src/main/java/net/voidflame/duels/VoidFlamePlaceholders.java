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
    private final java.util.Set<UUID> refreshInFlight = ConcurrentHashMap.newKeySet();

    private record Profile(long wins, long losses, long streak, long bestStreak, double elo, long coins, String rank) {
        static Profile empty() { return new Profile(0, 0, 0, 0, 1000.0D, 0, "Player"); }
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
        UUID uuid = player.getUniqueId();
        if (!player.isOnline() || !refreshInFlight.add(uuid)) return;
        var registration = Bukkit.getServicesManager().getRegistration(StorageService.class);
        if (registration == null || registration.getProvider() == null) {
            profiles.putIfAbsent(uuid, Profile.empty());
            refreshInFlight.remove(uuid);
            return;
        }
        registration.getProvider().query(
                "SELECT wins, losses, winstreak, best_winstreak, elo, rank FROM player_profiles WHERE uuid = ? LIMIT 1",
                uuid.toString()
        ).whenComplete((rows, error) -> {
            refreshInFlight.remove(uuid);
            // Database callbacks run asynchronously. Publish cache changes on the main
            // thread and ignore late results for players who have already disconnected.
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !uuid.equals(player.getUniqueId())) return;
                    if (error != null || rows == null || rows.isEmpty()) {
                        profiles.putIfAbsent(uuid, Profile.empty());
                        return;
                    }
                    Map<String, Object> row = rows.get(0);
                    Profile profile = new Profile(longNumber(row.get("wins")), longNumber(row.get("losses")),
                            longNumber(row.get("winstreak")), longNumber(row.get("best_winstreak")),
                            decimalNumber(row.get("elo"), 1000.0D), plugin.advancedFeatures().coinBalance(uuid),
                            row.get("rank") == null ? "Player" : String.valueOf(row.get("rank")));
                    profiles.put(uuid, profile);
                });
            } catch (RuntimeException schedulingFailure) {
                plugin.getLogger().fine("Skipping placeholder refresh after scheduler shutdown for " + uuid
                        + ": " + schedulingFailure.getMessage());
            }
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
            case "elo" -> formatElo(p.elo());
            case "coins" -> String.valueOf(p.coins());
            case "rank" -> p.rank();
            case "mode", "status" -> status(player.getUniqueId());
            case "queue" -> plugin.queueManager().isQueued(player.getUniqueId()) ? "Queued" : "Not Queued";
            case "party" -> plugin.partyManager().partyOf(player.getUniqueId()) == null ? "None" : "In Party";
            default -> null;
        };
    }

    private String status(UUID id) {
        if (plugin.matchManager().isInMatch(id)) return "In Duel";
        if (plugin.spectatorManager().isSpectating(id)) return "Spectating";
        if (plugin.ffaManager().isInFfa(id)) return "FFA";
        if (plugin.queueManager().isQueued(id)) return "In Queue";
        if (plugin.partyManager().partyOf(id) != null) return "In Party";
        return "Spawn";
    }

    private double decimalNumber(Object value, double fallback) {
        if (value instanceof Number n) {
            double parsed = n.doubleValue();
            return Double.isFinite(parsed) && parsed >= 0.0D ? parsed : fallback;
        }
        try {
            double parsed = value == null ? fallback : Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) && parsed >= 0.0D ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
    private String formatElo(double elo) {
        if (!Double.isFinite(elo)) return "1000";
        if (elo == Math.rint(elo) && elo >= Long.MIN_VALUE && elo <= Long.MAX_VALUE) return Long.toString((long) elo);
        return String.format(java.util.Locale.ROOT, "%.1f", elo);
    }
    private long longNumber(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
