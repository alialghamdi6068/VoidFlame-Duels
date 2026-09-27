package net.voidflame.duels;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectatorManager implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Match> watching = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSnapshot> pendingRestores = new ConcurrentHashMap<>();

    public SpectatorManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean spectate(Player spectator, Player target) {
        Match match = plugin.matchManager().get(target.getUniqueId());
        if (match == null || match.state() == MatchState.FINISHED) return false;
        if (plugin.matchManager().isInMatch(spectator.getUniqueId())) return false;
        leave(spectator);
        snapshots.put(spectator.getUniqueId(), PlayerSnapshot.capture(spectator));
        watching.put(spectator.getUniqueId(), match);
        spectator.getInventory().clear();
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setSpectatorTarget(target);
        spectator.sendMessage(plugin.message("spectating").replace("<player>", target.getName()));
        return true;
    }

    public boolean isSpectating(UUID uuid) {
        return watching.containsKey(uuid);
    }

    public boolean leave(Player spectator) {
        PlayerSnapshot snapshot = snapshots.remove(spectator.getUniqueId());
        watching.remove(spectator.getUniqueId());
        if (snapshot == null) return false;
        spectator.setSpectatorTarget(null);
        snapshot.restore(spectator);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PlayerSnapshot snapshot = snapshots.remove(id);
        if (snapshot != null) pendingRestores.put(id, snapshot);
        watching.remove(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PlayerSnapshot snapshot = pendingRestores.remove(id);
        if (snapshot != null) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> snapshot.restore(event.getPlayer()));
        }
    }

    public void stopWatching(Match match) {
        for (Map.Entry<UUID, Match> entry : watching.entrySet()) {
            if (entry.getValue() != match) continue;
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) leave(player);
            else {
                watching.remove(entry.getKey(), match);
                PlayerSnapshot snapshot = snapshots.remove(entry.getKey());
                if (snapshot != null) pendingRestores.put(entry.getKey(), snapshot);
            }
        }
    }

    public void shutdown() {
        for (UUID uuid : snapshots.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) leave(player);
        }
        for (UUID uuid : pendingRestores.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            PlayerSnapshot snapshot = pendingRestores.remove(uuid);
            if (player != null && snapshot != null) snapshot.restore(player);
        }
        snapshots.clear();
        watching.clear();
        pendingRestores.clear();
    }
}
