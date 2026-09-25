package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
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

    public MatchManager(VoidFlameDuelsPlugin plugin, QueueManager queues, ArenaManager arenas, KitManager kits) {
        this.plugin = plugin;
        this.queues = queues;
        this.arenas = arenas;
        this.kits = kits;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::matchmake, 20L, 20L);
    }

    private void matchmake() {
        for (KitType kit : KitType.values()) {
            if (queues.queued(kit) < 2) continue;
            UUID first = queues.poll(kit), second = queues.poll(kit);
            if (first == null || second == null) continue;
            Player a = Bukkit.getPlayer(first), b = Bukkit.getPlayer(second);
            if (a == null || b == null || !a.isOnline() || !b.isOnline()) continue;
            startDirect(a, b, kit);
        }
    }

    public boolean startDirect(Player a, Player b, KitType kit) {
        if (a.equals(b) || isInMatch(a.getUniqueId()) || isInMatch(b.getUniqueId())) return false;
        Arena arena = arenas.acquire();
        if (arena == null) {
            a.sendMessage("§cNo duel arena is available.");
            b.sendMessage("§cNo duel arena is available.");
            return false;
        }
        Match match = new Match(plugin, this, a.getUniqueId(), b.getUniqueId(), kit, arena);
        matches.put(a.getUniqueId(), match);
        matches.put(b.getUniqueId(), match);
        kits.apply(a, kit);
        kits.apply(b, kit);
        match.start();
        return true;
    }

    public boolean isInMatch(UUID id) { return matches.containsKey(id); }
    public Match get(UUID id) { return matches.get(id); }
    public KitType lastKit(UUID id) {
        Match m = disconnected.get(id);
        if (m != null) return m.kit();
        Match active = matches.get(id);
        return active == null ? KitType.SWORD : active.kit();
    }

    public int playersInMatches(KitType kit) {
        return (int) matches.values().stream().distinct().filter(m -> m.kit() == kit).count() * 2;
    }

    public int activeMatches() { return (int) matches.values().stream().distinct().count(); }

    void finish(Match match, UUID winner) {
        matches.remove(match.first());
        matches.remove(match.second());
        disconnected.remove(match.first());
        disconnected.remove(match.second());
        arenas.release(match.arena());
        Player w = Bukkit.getPlayer(winner);
        Player l = Bukkit.getPlayer(match.opponent(winner));
        if (w != null) w.sendMessage(plugin.message("match-ended").replace("<winner>", w.getName()).replace("<kit>", pretty(match.kit())));
        if (l != null) l.sendMessage(plugin.message("match-ended").replace("<winner>", w == null ? "Opponent" : w.getName()).replace("<kit>", pretty(match.kit())));
        if (w != null) plugin.rematches().remember(w.getUniqueId(), l == null ? match.opponent(winner) : l.getUniqueId());
        if (l != null) plugin.rematches().remember(l.getUniqueId(), winner);
    }

    void markDisconnected(Match match, UUID player) {
        matches.remove(player);
        disconnected.put(player, match);
        Player opponent = Bukkit.getPlayer(match.opponent(player));
        if (opponent != null) opponent.sendMessage(plugin.message("rejoin-available")
                .replace("<seconds>", String.valueOf(plugin.getConfig().getLong("settings.disconnect-grace-seconds", 30)))
                .replace("<opponent>", Bukkit.getOfflinePlayer(player).getName()));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (disconnected.remove(player, match)) {
                match.finish(match.opponent(player));
            }
        }, plugin.getConfig().getLong("settings.disconnect-grace-seconds", 30) * 20L);
    }

    public boolean rejoin(Player player) {
        Match match = disconnected.remove(player.getUniqueId());
        if (match == null || match.state() == MatchState.FINISHED) return false;
        matches.put(player.getUniqueId(), match);
        player.teleport(match.spawnFor(player.getUniqueId()));
        plugin.kitManager().apply(player, match.kit());
        player.sendMessage(plugin.message("duel-starting"));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        UUID loser = event.getEntity().getUniqueId();
        Match match = matches.get(loser);
        if (match == null || match.state() != MatchState.FIGHTING) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        match.finish(match.opponent(loser));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Match match = matches.get(id);
        if (match != null && match.state() != MatchState.FINISHED) markDisconnected(match, id);
    }

    public void shutdown() {
        matches.values().stream().distinct().forEach(m -> m.finish(m.first()));
        matches.clear();
        disconnected.clear();
    }

    private String pretty(KitType k) { return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_', ' '); }
}
