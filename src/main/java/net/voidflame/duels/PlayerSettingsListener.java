package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerSettingsListener implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    public PlayerSettingsListener(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.playerSettings().load(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (plugin.playerSettings().showPlayers(player)) player.showPlayer(plugin, other);
                else if (!player.equals(other)) player.hidePlayer(plugin, other);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Core owns the cache lifecycle; no local persistence exists here.
    }
}
