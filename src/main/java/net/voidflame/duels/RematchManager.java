package net.voidflame.duels;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RematchManager {
    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, UUID> lastOpponent = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pending = new ConcurrentHashMap<>();

    public RematchManager(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    public void remember(UUID player, UUID opponent) { lastOpponent.put(player, opponent); }

    public boolean send(Player sender) {
        UUID opponent = lastOpponent.get(sender.getUniqueId());
        if (opponent == null) return false;
        Player target = plugin.getServer().getPlayer(opponent);
        if (target == null) return false;
        pending.put(target.getUniqueId(), sender.getUniqueId());
        target.sendMessage(plugin.message("rematch-received").replace("<player>", sender.getName()));
        return true;
    }

    public boolean accept(Player target, Player sender) {
        UUID pendingSender = pending.get(target.getUniqueId());
        if (pendingSender == null || !pendingSender.equals(sender.getUniqueId())) return false;
        pending.remove(target.getUniqueId());
        KitType kit = plugin.matchManager().lastKit(sender.getUniqueId());
        return plugin.matchManager().startDirect(target, sender, kit);
    }
}
