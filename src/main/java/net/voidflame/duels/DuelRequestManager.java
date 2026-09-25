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
        incoming.put(target.getUniqueId(), new Request(sender.getUniqueId(), target.getUniqueId(), kit,
                System.currentTimeMillis() + plugin.getConfig().getLong("settings.request-expiry-seconds", 60) * 1000L));
        return true;
    }

    public Request get(Player target) {
        Request r = incoming.get(target.getUniqueId());
        if (r == null || r.expiresAt() < System.currentTimeMillis()) {
            incoming.remove(target.getUniqueId());
            return null;
        }
        return r;
    }

    public Request getFrom(Player target, Player sender) {
        Request r = get(target);
        return r != null && r.sender().equals(sender.getUniqueId()) ? r : null;
    }

    public void remove(Player target) { incoming.remove(target.getUniqueId()); }
}
