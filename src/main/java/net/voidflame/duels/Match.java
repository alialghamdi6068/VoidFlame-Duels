package net.voidflame.duels;

import org.bukkit.entity.Player;
import java.util.UUID;

public record Match(UUID first, UUID second, KitType kit) {
    private static final int COUNTDOWN = 5;

    public void start(VoidFlameDuelsPlugin plugin) {
        Player a = plugin.getServer().getPlayer(first), b = plugin.getServer().getPlayer(second);
        if (a == null || b == null) { end(plugin); return; }
        a.sendMessage("§bDuel found: §e" + kit);
        b.sendMessage("§bDuel found: §e" + kit);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player x = plugin.getServer().getPlayer(first), y = plugin.getServer().getPlayer(second);
            if (x != null) x.sendMessage("§aFight!");
            if (y != null) y.sendMessage("§aFight!");
        }, COUNTDOWN * 20L);
    }

    public void handleQuit(VoidFlameDuelsPlugin plugin) { end(plugin); }

    public void end(VoidFlameDuelsPlugin plugin) { plugin.matchManager().finish(this); }
}
