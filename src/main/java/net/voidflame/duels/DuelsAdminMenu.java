package net.voidflame.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/** Compatibility command entry point; all administration UI is native Dialog-based. */
public final class DuelsAdminMenu implements Listener {
    private final VoidFlameDuelsPlugin plugin;

    public DuelsAdminMenu(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!JavaAdminAccess.hasAdminAccess(player)) {
            player.sendMessage("§cYou do not have permission.");
            return;
        }
        plugin.nativeMenus().openAdmin(player);
    }
}
