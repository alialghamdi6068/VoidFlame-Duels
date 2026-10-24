package net.voidflame.duels;

import net.voidflame.core.api.KitService;
import net.voidflame.core.storage.PlayerProfile;
import net.voidflame.core.storage.PlayerProfileService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

public final class QueueManager implements Listener {
    public record QueueEntry(UUID player, KitType kit, boolean ranked, long joinedAt, double elo) {}

    private final VoidFlameDuelsPlugin plugin;
    private final Map<KitType, LinkedHashMap<UUID, QueueEntry>> queues = new EnumMap<>(KitType.class);
    private final Map<UUID, QueueEntry> playerQueues = new ConcurrentHashMap<>();
    private final Map<UUID, Double> eloCache = new ConcurrentHashMap<>();
    private final Set<UUID> eloLoading = ConcurrentHashMap.newKeySet();
    private PlayerProfileService profiles;
    private KitService kits;

    public QueueManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        for (KitType kit : KitType.values()) queues.put(kit, new LinkedHashMap<>());
        RegisteredServiceProvider<PlayerProfileService> registration =
                Bukkit.getServicesManager().getRegistration(PlayerProfileService.class);
        if (registration != null) profiles = registration.getProvider();
        RegisteredServiceProvider<KitService> kitRegistration = Bukkit.getServicesManager().getRegistration(KitService.class);
        if (kitRegistration != null) kits = kitRegistration.getProvider();
    }

    public synchronized boolean join(Player player, KitType kit) {
        return join(player, kit, false);
    }

    public synchronized boolean join(Player player, KitType kit, boolean ranked) {
        if (player == null || kit == null) return false;
        UUID id = player.getUniqueId();
        if (!player.isOnline() || playerQueues.containsKey(id) || plugin.matchManager().isInMatch(id)
                || plugin.matchManager().isDisconnected(id) || plugin.kitEditorManager().isEditing(id)
                || plugin.ffaManager().isInFfa(id) || plugin.spectatorManager().isSpectating(id)
                || plugin.partyManager().partyOf(id) != null) return false;
        if (!isKitEnabled(kit)) return false;
        if (ranked && (!plugin.getConfig().getBoolean("queue.ranked.enabled", true) || profiles == null)) return false;

        double elo = eloCache.getOrDefault(id, plugin.getConfig().getDouble("queue.ranked.default-elo", 1000.0));
        QueueEntry entry = new QueueEntry(id, kit, ranked, System.currentTimeMillis(), elo);
        queues.get(kit).put(id, entry);
        playerQueues.put(id, entry);
        loadElo(id, player.getName());
        return true;
    }

    public boolean isKitEnabled(KitType kit) {
        if (kit == null) return false;
        String id = kit.name().toLowerCase(Locale.ROOT);
        return plugin.getConfig().getBoolean("kits." + id + ".enabled", true)
                && (kits == null || kits.isEnabled(id));
    }

    private void loadElo(UUID id, String name) {
        if (profiles == null || !eloLoading.add(id)) return;
        try {
            profiles.load(id, name).whenComplete((profile, error) -> {
                boolean notifyFailure = false;
                synchronized (this) {
                    eloLoading.remove(id);
                    QueueEntry current = playerQueues.get(id);
                    if (error != null || profile == null || !Double.isFinite(profile.elo()) || profile.elo() < 0.0D) {
                        plugin.getLogger().warning("Could not load a valid ELO profile for queued player " + id
                                + (error == null ? "." : ": " + error.getMessage()));
                        if (current != null) {
                            leave(id);
                            notifyFailure = true;
                        }
                    } else {
                        double elo = profile.elo();
                        eloCache.put(id, elo);
                        if (current != null) {
                            QueueEntry refreshed = new QueueEntry(current.player(), current.kit(), current.ranked(), current.joinedAt(), elo);
                            LinkedHashMap<UUID, QueueEntry> queue = queues.get(current.kit());
                            if (queue != null && queue.containsKey(id)) queue.put(id, refreshed);
                            playerQueues.put(id, refreshed);
                        }
                    }
                }
                if (notifyFailure) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player player = plugin.getServer().getPlayer(id);
                        if (player != null && player.isOnline()) player.sendMessage(plugin.message("queue-expired"));
                    });
                }
            });
        } catch (RuntimeException error) {
            eloLoading.remove(id);
            plugin.getLogger().warning("Could not start ELO load for queued player " + id + ": " + error.getMessage());
            leave(id);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null && player.isOnline()) player.sendMessage(plugin.message("queue-expired"));
            });
        }
    }

    public synchronized boolean leave(Player player) {
        return leave(player.getUniqueId());
    }

    public synchronized boolean leave(UUID id) {
        QueueEntry entry = playerQueues.remove(id);
        return entry != null && queues.get(entry.kit()).remove(id) != null;
    }

    public synchronized UUID poll(KitType kit) {
        QueueEntry entry = pollEntry(kit, false);
        return entry == null ? null : entry.player();
    }

    public synchronized QueueEntry pollEntry(KitType kit, boolean ranked) {
        if (kits != null && !kits.isEnabled(kit.name().toLowerCase(Locale.ROOT))) return null;
        LinkedHashMap<UUID, QueueEntry> queue = queues.get(kit);
        if (queue == null || queue.isEmpty()) return null;

        QueueEntry selected = null;
        // pollEntry chooses the oldest eligible player. pollRankedPair then
        // searches the remaining queue for the closest ELO-compatible opponent.
        for (QueueEntry entry : queue.values()) {
            if (eloLoading.contains(entry.player())) continue;
            if (entry.ranked() == ranked) {
                selected = entry;
                break;
            }
        }
        if (selected == null) return null;
        queue.remove(selected.player());
        playerQueues.remove(selected.player());
        return selected;
    }

    public synchronized UUID[] pollRankedPair(KitType kit) {
        QueueEntry first = pollEntry(kit, true);
        if (first == null) return null;
        QueueEntry best = null;
        LinkedHashMap<UUID, QueueEntry> queue = queues.get(kit);
        long now = System.currentTimeMillis();
        double bestDiff = Double.MAX_VALUE;
        double baseRange = Math.max(1.0, plugin.getConfig().getDouble("queue.ranked.initial-elo-range", 100.0));
        for (QueueEntry candidate : queue.values()) {
            if (!candidate.ranked() || eloLoading.contains(candidate.player())) continue;
            double range = baseRange + ((now - Math.min(first.joinedAt(), candidate.joinedAt())) / 1000.0)
                    * Math.max(0.0, plugin.getConfig().getDouble("queue.ranked.elo-range-growth-per-second", 10.0));
            double diff = Math.abs(first.elo() - candidate.elo());
            if (diff <= range && diff < bestDiff) { best = candidate; bestDiff = diff; }
        }
        if (best == null) {
            requeueEntry(first);
            return null;
        }
        queue.remove(best.player());
        playerQueues.remove(best.player());
        return new UUID[]{first.player(), best.player()};
    }

    public synchronized UUID[] pollUnrankedPair(KitType kit) {
        QueueEntry first = pollEntry(kit, false);
        if (first == null) return null;
        QueueEntry second = pollEntry(kit, false);
        if (second == null) { requeueEntry(first); return null; }
        return new UUID[]{first.player(), second.player()};
    }

    public synchronized void requeue(UUID id, KitType kit, boolean ranked) {
        if (id == null || kit == null || playerQueues.containsKey(id)) return;
        Player p = plugin.getServer().getPlayer(id);
        if (p == null || !p.isOnline() || plugin.matchManager().isInMatch(id)) return;
        double elo = eloCache.getOrDefault(id, plugin.getConfig().getDouble("queue.ranked.default-elo", 1000.0));
        QueueEntry entry = new QueueEntry(id, kit, ranked, System.currentTimeMillis(), elo);
        queues.get(kit).put(id, entry);
        playerQueues.put(id, entry);
    }

    private void requeueEntry(QueueEntry entry) {
        if (entry == null) return;
        Player p = plugin.getServer().getPlayer(entry.player());
        if (p == null || !p.isOnline() || plugin.matchManager().isInMatch(entry.player())) return;
        queues.get(entry.kit()).put(entry.player(), entry);
        playerQueues.put(entry.player(), entry);
    }

    public KitType kitOf(UUID id) {
        QueueEntry entry = playerQueues.get(id);
        return entry == null ? null : entry.kit();
    }

    public boolean isRanked(UUID id) {
        QueueEntry entry = playerQueues.get(id);
        return entry != null && entry.ranked();
    }

    public boolean isQueued(UUID id) { return playerQueues.containsKey(id); }

    public int queued(KitType kit) { return queues.get(kit).size(); }

    public int queued(KitType kit, boolean ranked) {
        return (int) queues.get(kit).values().stream().filter(e -> e.ranked() == ranked).count();
    }

    public int totalQueued() { return playerQueues.size(); }

    public void expireStale() {
        long now = System.currentTimeMillis();
        long timeout = Math.max(1L, plugin.getConfig().getLong("settings.queue-timeout-seconds", 300L)) * 1000L;
        for (QueueEntry entry : new ArrayList<>(playerQueues.values())) {
            Player p = plugin.getServer().getPlayer(entry.player());
            if (p == null || !p.isOnline() || now - entry.joinedAt() >= timeout) {
                if (leave(entry.player()) && p != null && p.isOnline()) {
                    p.sendMessage(plugin.message("queue-expired"));
                    
                }
            }
        }
    }

    public void refreshElo(UUID id, double elo) { eloCache.put(id, elo); }

    public void shutdown() {
        synchronized (this) {
            queues.values().forEach(Map::clear);
            playerQueues.clear();
        }
        eloCache.clear();
        eloLoading.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { leave(event.getPlayer()); }
}