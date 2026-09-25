package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchManager implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final QueueManager queues;
    private final ArenaManager arenas;
    private final KitManager kits;
    private final Map<UUID, Match> matches = new ConcurrentHashMap<>();
    private final Map<UUID, Match> disconnected = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSnapshot> pendingRestores = new ConcurrentHashMap<>();
    private final Map<UUID, Long> disconnectTokens = new ConcurrentHashMap<>();

    public MatchManager(VoidFlameDuelsPlugin plugin, QueueManager queues, ArenaManager arenas, KitManager kits) {
        this.plugin = plugin;
        this.queues = queues;
        this.arenas = arenas;
        this.kits = kits;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::matchmake, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, queues::expireStale, 20L, 100L);
    }

    private void matchmake() {
        for (KitType kit : KitType.values()) {
            while (queues.queued(kit) >= 2) {
                UUID first = queues.poll(kit);
                UUID second = queues.poll(kit);
                if (first == null || second == null) break;
                Player a = Bukkit.getPlayer(first), b = Bukkit.getPlayer(second);
                if (a == null || !a.isOnline()) { queues.requeue(second, kit); continue; }
                if (b == null || !b.isOnline()) { queues.requeue(first, kit); continue; }
                if (!startDirect(a, b, kit)) {
                    queues.requeue(first, kit);
                    queues.requeue(second, kit);
                    break;
                }
            }
        }
    }

    public boolean startDirect(Player a, Player b, KitType kit) {
        if (a.equals(b) || !a.isOnline() || !b.isOnline()
                || isInMatch(a.getUniqueId()) || isInMatch(b.getUniqueId())
                || queues.isQueued(a.getUniqueId()) || queues.isQueued(b.getUniqueId())
                || plugin.spectatorManager().isSpectating(a.getUniqueId())
                || plugin.spectatorManager().isSpectating(b.getUniqueId())) return false;

        Arena arena = arenas.acquire();
        if (arena == null) {
            a.sendMessage(plugin.message("no-arena"));
            b.sendMessage(plugin.message("no-arena"));
            return false;
        }

        Match match = new Match(plugin, this, a.getUniqueId(), b.getUniqueId(), kit, arena,
                PlayerSnapshot.capture(a), PlayerSnapshot.capture(b));
        matches.put(a.getUniqueId(), match);
        matches.put(b.getUniqueId(), match);
        match.start();
        plugin.scoreboardManager().updateAll();
        return true;
    }

    public boolean isInMatch(UUID id) { return matches.containsKey(id); }
    public Match get(UUID id) { return matches.get(id); }

    public KitType lastKit(UUID id) {
        Match match = disconnected.get(id);
        return match == null ? KitType.SWORD : match.kit();
    }

    public int playersInMatches(KitType kit) {
        return (int) matches.values().stream().distinct().filter(m -> m.kit() == kit).count() * 2;
    }

    public int activeMatches() {
        return (int) matches.values().stream().distinct().count();
    }

    void finish(Match match, UUID winner) {
        PlayerSnapshot firstSnapshot = match.snapshot(match.first());
        PlayerSnapshot secondSnapshot = match.snapshot(match.second());

        matches.remove(match.first(), match);
        matches.remove(match.second(), match);
        disconnected.remove(match.first(), match);
        disconnected.remove(match.second(), match);
        disconnectTokens.remove(match.first());
        disconnectTokens.remove(match.second());

        restoreOrDefer(match.first(), firstSnapshot);
        restoreOrDefer(match.second(), secondSnapshot);

        Player a = Bukkit.getPlayer(match.first());
        Player b = Bukkit.getPlayer(match.second());
        arenas.release(match.arena());
        plugin.spectatorManager().stopWatching(match);

        if (winner == null) {
            if (a != null) a.sendMessage(plugin.message("match-draw"));
            if (b != null) b.sendMessage(plugin.message("match-draw"));
        } else {
            Player w = Bukkit.getPlayer(winner);
            String winnerName = w == null ? String.valueOf(Bukkit.getOfflinePlayer(winner).getName()) : w.getName();
            if (winnerName == null) winnerName = "Unknown";
            String message = plugin.message("match-ended").replace("<winner>", winnerName).replace("<kit>", pretty(match.kit()));
            if (a != null) a.sendMessage(message);
            if (b != null) b.sendMessage(message);
            plugin.rematches().remember(match.first(), match.second(), match.kit());
            plugin.rematches().remember(match.second(), match.first(), match.kit());
        }
        plugin.scoreboardManager().updateAll();
    }

    private void restoreOrDefer(UUID id, PlayerSnapshot snapshot) {
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) snapshot.restore(player);
        else pendingRestores.put(id, snapshot);
    }

    void markDisconnected(Match match, UUID player) {
        if (match.state() == MatchState.FINISHED) return;
        matches.remove(player, match);
        disconnected.put(player, match);
        long token = System.nanoTime();
        disconnectTokens.put(player, token);

        Player opponent = Bukkit.getPlayer(match.opponent(player));
        if (opponent != null) {
            opponent.sendMessage(plugin.message("rejoin-available")
                    .replace("<seconds>", String.valueOf(graceSeconds()))
                    .replace("<opponent>", name(player)));
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!Long.valueOf(token).equals(disconnectTokens.get(player))) return;
            if (disconnected.remove(player, match)) {
                disconnectTokens.remove(player, token);
                match.finish(match.opponent(player));
            }
        }, graceSeconds() * 20L);
        plugin.scoreboardManager().updateAll();
    }

    public boolean rejoin(Player player) {
        UUID id = player.getUniqueId();
        Match match = disconnected.remove(id);
        if (match == null || match.state() == MatchState.FINISHED) return false;
        disconnectTokens.remove(id);
        matches.put(id, match);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(match.state() == MatchState.COUNTDOWN);
        player.teleport(match.spawnFor(id));
        kits.apply(player, match.kit());
        player.sendMessage(plugin.message("rejoined"));
        plugin.scoreboardManager().updateAll();
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        UUID loser = event.getEntity().getUniqueId();
        Match match = matches.get(loser);
        if (match == null || match.state() != MatchState.FIGHTING) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDeathMessage(null);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (match.state() != MatchState.FINISHED) match.finish(match.opponent(loser));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        queues.leave(id);
        plugin.partyManager().leave(id);
        Match match = matches.get(id);
        if (match != null && match.state() != MatchState.FINISHED) markDisconnected(match, id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PlayerSnapshot snapshot = pendingRestores.remove(id);
        if (snapshot != null) plugin.getServer().getScheduler().runTask(plugin, () -> snapshot.restore(event.getPlayer()));
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.scoreboardManager().update(event.getPlayer()));
    }

    private String name(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? "Unknown" : name;
    }

    private long graceSeconds() {
        return Math.max(1, plugin.getConfig().getLong("settings.disconnect-grace-seconds", 30));
    }

    public void shutdown() {
        matches.values().stream().distinct().toList().forEach(m -> m.finish(null));
        matches.clear();
        disconnected.clear();
        disconnectTokens.clear();
        for (Map.Entry<UUID, PlayerSnapshot> entry : pendingRestores.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) entry.getValue().restore(p);
        }
        pendingRestores.clear();
    }

    private String pretty(KitType k) { return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_', ' '); }
}
