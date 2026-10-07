package net.voidflame.duels;

import net.voidflame.core.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FfaManager implements Listener {
    private static final class Session {
        private final PlayerSnapshot snapshot;
        private final String kit;
        private long kills;
        private long deaths;
        private long streak;
        Session(PlayerSnapshot snapshot, String kit, long kills, long deaths, long streak) {
            this.snapshot = snapshot; this.kit = kit; this.kills = kills; this.deaths = deaths; this.streak = streak;
        }
    }

    private final VoidFlameDuelsPlugin plugin;
    private final StorageService storage;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastKiller = new ConcurrentHashMap<>();

    public FfaManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<StorageService> registration =
                Bukkit.getServicesManager().getRegistration(StorageService.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("VoidFlame-Core StorageService is unavailable.");
        }
        this.storage = registration.getProvider();
    }

    public boolean join(Player player, String kitName) {
        if (!plugin.getConfig().getBoolean("features.ffa.enabled", true)) {
            player.sendMessage(plugin.message("ffa-disabled")); return false;
        }
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) return true;
        if (plugin.matchManager().isInMatch(id)) {
            player.sendMessage(plugin.message("already-in-match")); return false;
        }
        if (plugin.queueManager().isQueued(id) || plugin.spectatorManager().isSpectating(id)
                || plugin.partyManager().partyOf(id) != null) {
            player.sendMessage(plugin.message("already-in-match")); return false;
        }
        World world = Bukkit.getWorld(plugin.getConfig().getString("features.ffa.world", "ffa"));
        if (world == null) {
            player.sendMessage(plugin.message("ffa-world-missing")); return false;
        }
        int max = Math.max(1, plugin.getConfig().getInt("features.ffa.max-players", 32));
        if (sessions.size() >= max) {
            player.sendMessage(plugin.message("ffa-full")); return false;
        }

        String kit = normalizeKit(kitName);
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        Stats stats = loadStats(player.getUniqueId());
        Session session = new Session(snapshot, kit, stats.kills, stats.deaths, stats.streak);
        sessions.put(id, session);

        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        player.teleport(world.getSpawnLocation());
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        plugin.kitManager().apply(player, parseKit(kit));
        player.sendMessage(plugin.message("ffa-joined").replace("<kit>", pretty(kit)));
        return true;
    }

    public boolean leave(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        lastKiller.remove(player.getUniqueId());
        if (session == null) return false;
        session.snapshot.restore(player);
        player.sendMessage(plugin.message("ffa-left"));
        persist(player.getUniqueId(), session);
        return true;
    }

    public boolean isInFfa(UUID uuid) { return sessions.containsKey(uuid); }
    public int onlineCount() { return sessions.size(); }

    public void sendStats(Player player) {
        loadStatsAsync(player.getUniqueId()).thenAccept(stats -> Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(color("&8&m--------------------"));
            player.sendMessage(color("&bVoidFlame &fFFA Stats"));
            player.sendMessage(color("&7Kills: &f" + stats.kills));
            player.sendMessage(color("&7Deaths: &f" + stats.deaths));
            player.sendMessage(color("&7Streak: &d" + stats.streak));
            player.sendMessage(color("&8&m--------------------"));
        }));
    }

    private java.util.concurrent.CompletableFuture<Stats> loadStatsAsync(UUID uuid) {
        return storage.get("duels.ffa", uuid.toString()).thenApply(raw -> {
            if (raw == null) return new Stats(0, 0, 0);
            try {
                String[] p = raw.split(",", -1);
                if (p.length != 3) return new Stats(0, 0, 0);
                return new Stats(Math.max(0, Long.parseLong(p[0])), Math.max(0, Long.parseLong(p[1])), Math.max(0, Long.parseLong(p[2])));
            } catch (Exception ignored) {
                return new Stats(0, 0, 0);
            }
        });
    }

    public void shutdown() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) leave(p);
        }
        sessions.clear();
        lastKiller.clear();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Session session = sessions.get(victim.getUniqueId());
        if (session == null) return;

        Player killer = victim.getKiller();
        if (killer != null && sessions.containsKey(killer.getUniqueId()) && !killer.equals(victim)) {
            Session killerSession = sessions.get(killer.getUniqueId());
            killerSession.kills++;
            killerSession.streak++;
            session.deaths++;
            session.streak = 0;
            lastKiller.put(victim.getUniqueId(), killer.getUniqueId());
            persist(killer.getUniqueId(), killerSession);
            persist(victim.getUniqueId(), session);
            playerMessage(killer, "ffa-kill", "<player>", victim.getName());
            rewardFfa(killer, "kill");
        } else {
            session.deaths++;
            session.streak = 0;
            persist(victim.getUniqueId(), session);
        }

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        World world = Bukkit.getWorld(plugin.getConfig().getString("features.ffa.world", "ffa"));
        if (world == null) return;
        event.setRespawnLocation(world.getSpawnLocation());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!sessions.containsKey(player.getUniqueId())) return;
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setFireTicks(0);
            plugin.kitManager().apply(player, parseKit(session.kit));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Session session = sessions.remove(id);
        lastKiller.remove(id);
        if (session != null) {
            // Never persist the temporary FFA kit as the player's real inventory.
            // Restore before the disconnect is saved by Minecraft.
            session.snapshot.restore(event.getPlayer());
            persist(id, session);
        }
    }

    private String normalizeKit(String raw) {
        if (raw == null || raw.isBlank()) return "sword";
        return raw.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private KitType parseKit(String raw) {
        try { return KitType.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return KitType.SWORD; }
    }

    private String pretty(String raw) { return raw.replace('_', ' '); }

    private void playerMessage(Player player, String key, String placeholder, String value) {
        player.sendMessage(plugin.message(key).replace(placeholder, value));
    }

    private Stats loadStats(UUID uuid) {
        try {
            String raw = storage.get("duels.ffa", uuid.toString()).getNow(null);
            if (raw == null) return new Stats(0, 0, 0);
            String[] p = raw.split(",", -1);
            if (p.length != 3) return new Stats(0, 0, 0);
            return new Stats(Math.max(0, Long.parseLong(p[0])), Math.max(0, Long.parseLong(p[1])), Math.max(0, Long.parseLong(p[2])));
        } catch (Exception ignored) { return new Stats(0, 0, 0); }
    }

    private void persist(UUID uuid, Session session) {
        storage.put("duels.ffa", uuid.toString(),
                session.kills + "," + session.deaths + "," + session.streak);
    }

    private void rewardFfa(Player player, String outcome) {
        String key = "rewards.ffa." + outcome;
        if (!plugin.getConfig().getBoolean(key + ".enabled", true)) return;
        int coins = Math.max(0, plugin.getConfig().getInt(key + ".coins", 0));
        if (coins > 0) plugin.advancedFeatures().addCoins(player.getUniqueId(), coins);
        String message = plugin.getConfig().getString(key + ".message", "");
        if (!message.isBlank()) player.sendMessage(color(message.replace("<coins>", String.valueOf(coins))));
    }

    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }

    private record Stats(long kills, long deaths, long streak) {}
}
