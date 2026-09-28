package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BotDuelManager implements Listener {
    private static final class Session {
        final PlayerSnapshot snapshot;
        final UUID botId;
        final String kit;
        Session(PlayerSnapshot snapshot, UUID botId, String kit) {
            this.snapshot = snapshot; this.botId = botId; this.kit = kit;
        }
    }

    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> botOwners = new ConcurrentHashMap<>();

    public BotDuelManager(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    public boolean start(Player player, String kitName) {
        if (!plugin.getConfig().getBoolean("features.botduels.enabled", true)) {
            player.sendMessage(plugin.message("bot-disabled")); return false;
        }
        if (sessions.containsKey(player.getUniqueId()) || plugin.matchManager().isInMatch(player.getUniqueId())
                || plugin.advancedFeatures().isGoldenHard(player.getUniqueId())) {
            player.sendMessage(plugin.message("already-in-match")); return false;
        }

        String kit = normalizeKit(kitName);
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        player.closeInventory();
        plugin.kitManager().apply(player, parseKit(kit));

        var world = player.getWorld();
        var spawn = player.getLocation().clone().add(0, 0, 4);
        Zombie bot = world.spawn(spawn, Zombie.class, entity -> {
            entity.setCustomName(color("&cVoidFlame Bot &7[" + pretty(kit) + "]"));
            entity.setCustomNameVisible(true);
            entity.setCanPickupItems(false);
            entity.setRemoveWhenFarAway(false);
            entity.setTarget(player);
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            entity.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            entity.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            entity.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            entity.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
            entity.getEquipment().setItemInMainHandDropChance(0);
            entity.getEquipment().setHelmetDropChance(0);
            entity.getEquipment().setChestplateDropChance(0);
            entity.getEquipment().setLeggingsDropChance(0);
            entity.getEquipment().setBootsDropChance(0);
        });
        sessions.put(player.getUniqueId(), new Session(snapshot, bot.getUniqueId(), kit));
        botOwners.put(bot.getUniqueId(), player.getUniqueId());

        player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage(plugin.message("bot-started").replace("<kit>", pretty(kit)));
        return true;
    }

    public boolean stop(Player player, boolean restore) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return false;
        botOwners.remove(session.botId);
        Entity bot = Bukkit.getEntity(session.botId);
        if (bot != null) bot.remove();
        if (restore) session.snapshot.restore(player);
        return true;
    }

    public boolean isActive(UUID player) { return sessions.containsKey(player); }

    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            Session session = sessions.remove(id);
            if (session == null) continue;
            botOwners.remove(session.botId);
            Entity bot = Bukkit.getEntity(session.botId);
            if (bot != null) bot.remove();
            if (player != null) session.snapshot.restore(player);
        }
        botOwners.clear();
    }

    @EventHandler
    public void onBotDeath(EntityDeathEvent event) {
        UUID owner = botOwners.remove(event.getEntity().getUniqueId());
        if (owner == null) return;
        event.getDrops().clear();
        Player player = Bukkit.getPlayer(owner);
        Session session = sessions.remove(owner);
        if (session == null) return;
        if (player != null && player.isOnline()) {
            player.sendMessage(plugin.message("bot-win"));
            plugin.advancedFeatures().addCoins(owner,
                    Math.max(0, plugin.getConfig().getInt("rewards.bot.win-coins", 25)));
            session.snapshot.restore(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID owner = event.getEntity().getUniqueId();
        Session session = sessions.remove(owner);
        if (session == null) return;
        botOwners.remove(session.botId);
        Entity bot = Bukkit.getEntity(session.botId);
        if (bot != null) bot.remove();
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDeathMessage(null);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                session.snapshot.restore(player);
                player.sendMessage(plugin.message("bot-loss"));
            }
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), false);
    }

    private String normalizeKit(String raw) {
        return raw == null || raw.isBlank() ? "sword" : raw.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private KitType parseKit(String raw) {
        try { return KitType.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return KitType.SWORD; }
    }

    private String pretty(String raw) { return raw.replace('_', ' '); }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
}
