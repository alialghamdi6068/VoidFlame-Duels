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
        if (sender.equals(target)) return false;
        if (plugin.matchManager().isInMatch(sender.getUniqueId()) || plugin.matchManager().isInMatch(target.getUniqueId())) return false;
        if (plugin.queueManager().isQueued(sender.getUniqueId()) || plugin.queueManager().isQueued(target.getUniqueId())) return false;
        if (plugin.partyManager().partyOf(sender.getUniqueId()) != null || plugin.partyManager().partyOf(target.getUniqueId()) != null) return false;
        long ttl = Math.max(1, plugin.getConfig().getLong("settings.request-expiry-seconds", 60));
        incoming.put(target.getUniqueId(), new Request(sender.getUniqueId(), target.getUniqueId(), kit, System.currentTimeMillis() + ttl * 1000L));
        return true;
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
