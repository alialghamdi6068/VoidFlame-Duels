package net.voidflame.duels;

import net.voidflame.core.storage.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fast cached access to per-player settings stored by VoidFlame-Core. */
public final class PlayerSettings {
    private static final Map<String, Boolean> DEFAULTS = Map.of(
            "duel_requests", true,
            "party_invites", true,
            "explosion_effects", false,
            "personal_level", false,
            "friend_requests", false,
            "private_messages", false,
            "friend_join_notifications", true,
            "scoreboard", true,
            "show_players", true
    );

    private final PlayerSettingsService service;

    public PlayerSettings() {
        RegisteredServiceProvider<PlayerSettingsService> registration =
                Bukkit.getServicesManager().getRegistration(PlayerSettingsService.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("VoidFlame-Core PlayerSettingsService is unavailable.");
        }
        service = registration.getProvider();
    }

    public boolean getCached(UUID uuid, String key) {
        return service.getCached(uuid, key, DEFAULTS.getOrDefault(key, true));
    }

    public boolean getCached(Player player, String key) {
        return getCached(player.getUniqueId(), key);
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        for (Map.Entry<String, Boolean> entry : DEFAULTS.entrySet()) {
            service.get(uuid, entry.getKey(), entry.getValue()).exceptionally(error -> null);
        }
    }

    public boolean duelRequests(Player player) { return getCached(player, "duel_requests"); }
    public boolean partyInvites(Player player) { return getCached(player, "party_invites"); }
    public boolean explosionEffects(Player player) { return getCached(player, "explosion_effects"); }
    public boolean personalLevel(Player player) { return getCached(player, "personal_level"); }
    public boolean friendRequests(Player player) { return getCached(player, "friend_requests"); }
    public boolean privateMessages(Player player) { return getCached(player, "private_messages"); }
    public boolean friendJoinNotifications(Player player) { return getCached(player, "friend_join_notifications"); }
    public boolean scoreboard(Player player) { return getCached(player, "scoreboard"); }
    public boolean showPlayers(Player player) { return getCached(player, "show_players"); }
}
