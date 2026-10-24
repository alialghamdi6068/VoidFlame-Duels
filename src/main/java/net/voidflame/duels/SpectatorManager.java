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
        if (spectator == null || target == null || spectator.equals(target)) return false;
        UUID id = spectator.getUniqueId();
        Match match = plugin.matchManager().get(target.getUniqueId());
        if (match == null || match.state() == MatchState.FINISHED || isSpectating(id)) return false;
        if (plugin.matchManager().isInMatch(id) || plugin.matchManager().isDisconnected(id)
                || plugin.queueManager().isQueued(id) || plugin.ffaManager().isInFfa(id)
                || plugin.kitEditorManager().isEditing(id) || plugin.partyManager().partyOf(id) != null) return false;
        snapshots.put(id, PlayerSnapshot.capture(spectator));
        watching.put(id, match);
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
        if (spectator == null) return false;
        UUID id = spectator.getUniqueId();
        PlayerSnapshot snapshot = snapshots.remove(id);
        watching.remove(id);
        if (snapshot == null) return false;
        try {
            spectator.setSpectatorTarget(null);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not clear spectator target for " + id + ": " + error.getMessage());
        }
        // Shared recovery retains the snapshot if restoration fails.
        plugin.matchManager().restorePartySnapshot(id, snapshot);
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
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.matchManager().restorePartySnapshot(id, snapshot));
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
        for (Map.Entry<UUID, PlayerSnapshot> entry : snapshots.entrySet()) {
            UUID id = entry.getKey();
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline()) {
                leave(player);
            } else {
                pendingRestores.putIfAbsent(id, entry.getValue());
                snapshots.remove(id, entry.getValue());
            }
        }
        for (Map.Entry<UUID, PlayerSnapshot> entry : pendingRestores.entrySet()) {
            if (pendingRestores.remove(entry.getKey(), entry.getValue())) {
                plugin.matchManager().restorePartySnapshot(entry.getKey(), entry.getValue());
            }
        }
        snapshots.clear();
        watching.clear();
        pendingRestores.clear();
    }
}
