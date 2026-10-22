package net.voidflame.duels;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Compatibility entry point for the duel menu.
 * Java players are routed to native Paper Dialogs and Bedrock players to Floodgate Forms.
 * No legacy inventory queue GUI is created or handled here.
 */
public final class DuelMenu implements Listener {
    private final VoidFlameDuelsPlugin plugin;

    public DuelMenu(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!JavaAdminAccess.isBedrock(player)) {
            plugin.nativeMenus().openQueue(player);
            return;
        }
        if (plugin.bedrockMenus().available()) {
            plugin.bedrockMenus().openQueue(player);
            return;
        }
        player.sendMessage("§cBedrock Forms are unavailable. Please contact staff.");
    }
}
