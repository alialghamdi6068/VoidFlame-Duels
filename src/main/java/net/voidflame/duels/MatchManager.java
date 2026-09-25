package net.voidflame.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchManager implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final QueueManager queues;
    private final Map<UUID, Match> matches = new ConcurrentHashMap<>();

    public MatchManager(VoidFlameDuelsPlugin plugin, QueueManager queues) {
        this.plugin = plugin; this.queues = queues;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::matchmake, 20L, 20L);
    }

    private void matchmake() {
        for (KitType kit : KitType.values()) {
            if (queues.queued(kit) < 2) continue;
            UUID first = queues.poll(kit), second = queues.poll(kit);
            if (first == null || second == null) continue;
            Player a = plugin.getServer().getPlayer(first), b = plugin.getServer().getPlayer(second);
            if (a == null || b == null || !a.isOnline() || !b.isOnline()) continue;
            Match match = new Match(first, second, kit);
            matches.put(first, match); matches.put(second, match);
            match.start(plugin);
        }
    }

    public boolean isInMatch(UUID id) { return matches.containsKey(id); }
    public Match get(UUID id) { return matches.get(id); }
    public int activeMatches() { return (int) matches.values().stream().distinct().count(); }

    void finish(Match match) { matches.remove(match.first()); matches.remove(match.second()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Match match = matches.get(event.getPlayer().getUniqueId());
        if (match != null) match.handleQuit(plugin);
    }

    public void shutdown() {
        matches.values().stream().distinct().forEach(m -> m.end(plugin));
        matches.clear();
    }
}
