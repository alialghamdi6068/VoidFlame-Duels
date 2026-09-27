package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Match-aware combat tag. This is plugin-layer combat state only; it is not
 * network/DDoS protection.
 */
public final class CombatTagManager implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();

    public CombatTagManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        long interval = Math.max(1L, plugin.getConfig().getLong("combat-tag.cleanup-interval-ticks", 20L));
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, interval, interval);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("combat-tag.enabled", true)) return;
        Player victim = asPlayer(event.getEntity());
        Player attacker = asPlayer(event.getDamager());
        if (attacker == null && victim == null) return;
        if (attacker != null && plugin.matchManager().isInMatch(attacker.getUniqueId())) tag(attacker);
        if (victim != null && plugin.matchManager().isInMatch(victim.getUniqueId())) tag(victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isTagged(event.getPlayer().getUniqueId())) return;
        String command = event.getMessage().substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        boolean allowed = plugin.getConfig().getStringList("combat-tag.allowed-commands").stream()
                .map(s -> s.toLowerCase(Locale.ROOT).replace("/", ""))
                .anyMatch(command::equals);
        if (!allowed) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color(plugin.getConfig().getString(
                    "combat-tag.blocked-message", "&cYou cannot use that command while combat tagged.")));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!isTagged(event.getPlayer().getUniqueId())) return;
        if (plugin.getConfig().getBoolean("combat-tag.quit-counts-as-loss", true)
                && plugin.matchManager().isInMatch(event.getPlayer().getUniqueId())) {
            // MatchManager owns the actual winner/loser transition. We only keep
            // the combat-tag state for observability and external integrations.
            tag(event.getPlayer());
        }
    }

    public void tag(Player player) {
        if (player == null) return;
        long seconds = Math.max(1L, plugin.getConfig().getLong("combat-tag.duration-seconds", 15L));
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean isTagged(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            taggedUntil.remove(uuid, until);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) return 0L;
        return Math.max(0L, (until - System.currentTimeMillis() + 999L) / 1000L);
    }

    public void clear(UUID uuid) {
        taggedUntil.remove(uuid);
    }

    public void clearAll() {
        taggedUntil.clear();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        taggedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private Player asPlayer(Entity entity) {
        return entity instanceof Player player ? player : null;
    }

    private String color(String value) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', value);
    }
}
