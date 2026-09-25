package net.voidflame.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueManager implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final Map<KitType, java.util.LinkedHashSet<UUID>> queues = new EnumMap<>(KitType.class);
    private final Map<UUID, KitType> playerQueues = new ConcurrentHashMap<>();

    public QueueManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        for (KitType kit : KitType.values()) queues.put(kit, new java.util.LinkedHashSet<>());
    }

    public synchronized boolean join(Player player, KitType kit) {
        if (playerQueues.containsKey(player.getUniqueId())) return false;
        if (plugin.matchManager().isInMatch(player.getUniqueId())) return false;
        queues.get(kit).add(player.getUniqueId());
        playerQueues.put(player.getUniqueId(), kit);
        return true;
    }

    public synchronized boolean leave(Player player) {
        KitType kit = playerQueues.remove(player.getUniqueId());
        return kit != null && queues.get(kit).remove(player.getUniqueId());
    }

    public synchronized UUID poll(KitType kit) {
        var queue = queues.get(kit);
        var it = queue.iterator();
        if (!it.hasNext()) return null;
        UUID id = it.next(); it.remove(); playerQueues.remove(id); return id;
    }

    public int queued(KitType kit) { return queues.get(kit).size(); }
    public int totalQueued() { return playerQueues.size(); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { leave(event.getPlayer()); }

    public synchronized void shutdown() { queues.values().forEach(java.util.Set::clear); playerQueues.clear(); }
}
