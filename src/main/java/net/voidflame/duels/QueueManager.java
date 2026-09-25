package net.voidflame.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueManager implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final Map<KitType, LinkedHashSet<UUID>> queues = new EnumMap<>(KitType.class);
    private final Map<UUID, KitType> playerQueues = new ConcurrentHashMap<>();

    public QueueManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        for (KitType kit : KitType.values()) queues.put(kit, new LinkedHashSet<>());
    }

    public synchronized boolean join(Player player, KitType kit) {
        UUID id = player.getUniqueId();
        if (!player.isOnline() || playerQueues.containsKey(id) || plugin.matchManager().isInMatch(id)) return false;
        queues.get(kit).add(id);
        playerQueues.put(id, kit);
        return true;
    }

    public synchronized boolean leave(Player player) {
        return leave(player.getUniqueId());
    }

    public synchronized boolean leave(UUID id) {
        KitType kit = playerQueues.remove(id);
        return kit != null && queues.get(kit).remove(id);
    }

    public synchronized UUID poll(KitType kit) {
        Iterator<UUID> it = queues.get(kit).iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            it.remove();
            playerQueues.remove(id);
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.isOnline()) return id;
        }
        return null;
    }

    public synchronized void requeue(UUID id, KitType kit) {
        if (playerQueues.containsKey(id)) return;
        Player p = plugin.getServer().getPlayer(id);
        if (p == null || !p.isOnline() || plugin.matchManager().isInMatch(id)) return;
        queues.get(kit).add(id);
        playerQueues.put(id, kit);
    }

    public KitType kitOf(UUID id) { return playerQueues.get(id); }
    public boolean isQueued(UUID id) { return playerQueues.containsKey(id); }
    public int queued(KitType kit) { return queues.get(kit).size(); }
    public int totalQueued() { return playerQueues.size(); }

    public void expireStale() {
        for (UUID id : playerQueues.keySet()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p == null || !p.isOnline()) leave(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { leave(event.getPlayer()); }

    public synchronized void shutdown() {
        queues.values().forEach(java.util.Set::clear);
        playerQueues.clear();
    }
}
