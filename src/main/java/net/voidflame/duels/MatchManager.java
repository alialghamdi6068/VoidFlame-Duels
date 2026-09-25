package net.voidflame.duels;

import org.bukkit.Bukkit;
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
                || queues.isQueued(a.getUniqueId()) || queues.isQueued(b.getUniqueId())) return false;
        Arena arena = arenas.acquire();
        if (arena == null) {
            a.sendMessage(plugin.message("no-arena"));
            b.sendMessage(plugin.message("no-arena"));
            return false;
        }
        PlayerSnapshot firstSnapshot = PlayerSnapshot.capture(a);
        PlayerSnapshot secondSnapshot = PlayerSnapshot.capture(b);
        Match match = new Match(plugin, this, a.getUniqueId(), b.getUniqueId(), kit, arena, firstSnapshot, secondSnapshot);
        matches.put(a.getUniqueId(), match);
        matches.put(b.getUniqueId(), match);
        match.start();
        return true;
    }

    public boolean isInMatch(UUID id) { return matches.containsKey(id); }
    public Match get(UUID id) { return matches.get(id); }

    public KitType lastKit(UUID id) {
        Match m = disconnected.get(id);
        return m != null ? m.kit() : KitType.SWORD;
    }

    public int playersInMatches(KitType kit) {
        return (int) matches.values().stream().distinct().filter(m -> m.kit() == kit).count() * 2;
    }

    public int activeMatches() {
        return (int) matches.values().stream().distinct().count();
    }

    void finish(Match match, UUID winner) {
        matches.remove(match.first(), match);
        matches.remove(match.second(), match);
        disconnected.remove(match.first(), match);
        disconnected.remove(match.second(), match);

        Player w = winner == null ? null : Bukkit.getPlayer(winner);
        Player l = winner == null ? null : Bukkit.getPlayer(match.opponent(winner));

        Player a = Bukkit.getPlayer(match.first());
        Player b = Bukkit.getPlayer(match.second());
        restoreOrDefer(a, match.first());
        restoreOrDefer(b, match.second());

        arenas.release(match.arena());
        plugin.spectatorManager().stopWatching(match);

        if (winner == null) {
            if (a != null) a.sendMessage(plugin.message("match-draw"));
            if (b != null) b.sendMessage(plugin.message("match-draw"));
        } else {
            String winnerName = w == null ? Bukkit.getOfflinePlayer(winner).getName() : w.getName();
            if (a != null) a.sendMessage(plugin.message("match-ended").replace("<winner>", winnerName).replace("<kit>", pretty(match.kit())));
            if (b != null) b.sendMessage(plugin.message("match-ended").replace("<winner>", winnerName).replace("<kit>", pretty(match.kit())));
            plugin.rematches().remember(match.first(), match.second(), match.kit());
            plugin.rematches().remember(match.second(), match.first(), match.kit());
        }
        plugin.scoreboardManager().updateAll();
    }

    private void restoreOrDefer(Player player, UUID id) {
        if (player != null && player.isOnline()) matchForRestore(id).restore(player);
        else {
            Match m = findMatch(id);
            if (m != null) pendingRestores.put(id, snapshotFor(m, id));
        }
    }

    private PlayerSnapshot snapshotFor(Match m, UUID id) {
        return id.equals(m.first()) ? getSnapshot(m, true) : getSnapshot(m, false);
    }

    private PlayerSnapshot getSnapshot(Match m, boolean first) {
        try {
            var field = Match.class.getDeclaredField(first ? "firstSnapshot" : "secondSnapshot");
            field.setAccessible(true);
            return (PlayerSnapshot) field.get(m);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access match snapshot", e);
        }
    }

    private Match findMatch(UUID id) {
        for (Match m : disconnected.values()) if (m.first().equals(id) || m.second().equals(id)) return m;
        return null;
    }

    private MatchRestore matchForRestore(UUID id) {
        Match m = matches.get(id);
        if (m != null) return new MatchRestore(m, id);
        PlayerSnapshot snapshot = pendingRestores.remove(id);
        if (snapshot != null) return new MatchRestore(snapshot);
        throw new IllegalStateException("Missing restore snapshot for " + id);
    }

    private record MatchRestore(Match match, UUID id, PlayerSnapshot snapshot) {
        MatchRestore(Match match, UUID id) { this(match, id, null); }
        MatchRestore(PlayerSnapshot snapshot) { this(null, null, snapshot); }
        void restore(Player player) {
            if (snapshot != null) snapshot.restore(player);
            else match.restore(player);
        }
    }

    void markDisconnected(Match match, UUID player) {
        if (match.state() == MatchState.FINISHED) return;
        matches.remove(player, match);
        disconnected.put(player, match);
        Player opponent = Bukkit.getPlayer(match.opponent(player));
        if (opponent != null) opponent.sendMessage(plugin.message("rejoin-available")
                .replace("<seconds>", String.valueOf(graceSeconds()))
                .replace("<opponent>", Bukkit.getOfflinePlayer(player).getName()));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (disconnected.remove(player, match)) match.finish(match.opponent(player));
        }, graceSeconds() * 20L);
    }

    public boolean rejoin(Player player) {
        Match match = disconnected.remove(player.getUniqueId());
        if (match == null || match.state() == MatchState.FINISHED) return false;
        matches.put(player.getUniqueId(), match);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setInvulnerable(match.state() == MatchState.COUNTDOWN);
        player.teleport(match.spawnFor(player.getUniqueId()));
        kits.apply(player, match.kit());
        player.sendMessage(plugin.message("rejoined"));
        plugin.scoreboardManager().update(player);
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
        match.finish(match.opponent(loser));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        queues.leave(id);
        Match match = matches.get(id);
        if (match != null && match.state() != MatchState.FINISHED) markDisconnected(match, id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PlayerSnapshot snapshot = pendingRestores.remove(id);
        if (snapshot != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> snapshot.restore(event.getPlayer()));
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.scoreboardManager().update(event.getPlayer()));
    }

    private long graceSeconds() { return Math.max(1, plugin.getConfig().getLong("settings.disconnect-grace-seconds", 30)); }

    public void shutdown() {
        matches.values().stream().distinct().forEach(m -> m.finish(null));
        matches.clear();
        disconnected.clear();
        for (Map.Entry<UUID, PlayerSnapshot> entry : pendingRestores.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) entry.getValue().restore(p);
        }
        pendingRestores.clear();
    }

    private String pretty(KitType k) { return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_', ' '); }
}
