package net.voidflame.duels;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DuelRequestManager {
    public record Request(UUID sender, UUID target, KitType kit, long expiresAt) {}

    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, Request> incoming = new ConcurrentHashMap<>();

    public DuelRequestManager(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    public boolean send(Player sender, Player target, KitType kit) {
        if (sender == null || target == null || kit == null || sender.equals(target)
                || !sender.isOnline() || !target.isOnline()) return false;
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (unavailable(senderId) || unavailable(targetId)) return false;
        if (plugin.partyManager().partyOf(senderId) != null || plugin.partyManager().partyOf(targetId) != null) return false;
        long ttl = Math.max(1L, Math.min(3600L,
                plugin.getConfig().getLong("settings.request-expiry-seconds", 60L)));
        long now = System.currentTimeMillis();
        Request request = new Request(senderId, targetId, kit, now + ttl * 1000L);
        incoming.compute(targetId, (id, existing) ->
                existing == null || existing.expiresAt() <= now ? request : existing);
        return incoming.get(targetId) == request;
    }

    private boolean unavailable(UUID id) {
        return plugin.matchManager().isInMatch(id)
                || plugin.matchManager().isDisconnected(id)
                || plugin.queueManager().isQueued(id)
                || plugin.kitEditorManager().isEditing(id)
                || plugin.spectatorManager().isSpectating(id)
                || plugin.ffaManager().isInFfa(id);
    }

    public Request get(Player target) {
        Request request = incoming.get(target.getUniqueId());
        if (request == null) return null;
        if (request.expiresAt() <= System.currentTimeMillis()) {
            incoming.remove(target.getUniqueId(), request);
            return null;
        }
        Player sender = plugin.getServer().getPlayer(request.sender());
        if (sender == null || !sender.isOnline()) {
            incoming.remove(target.getUniqueId(), request);
            return null;
        }
        return request;
    }

    public Request getFrom(Player target, Player sender) {
        Request request = get(target);
        return request != null && request.sender().equals(sender.getUniqueId()) ? request : null;
    }

    public void remove(Player target) { incoming.remove(target.getUniqueId()); }
    public void clear() { incoming.clear(); }
}
