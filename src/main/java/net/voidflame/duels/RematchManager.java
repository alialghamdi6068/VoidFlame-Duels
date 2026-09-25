package net.voidflame.duels;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RematchManager {
    private record History(UUID opponent, KitType kit, long expiresAt) {}
    private record Pending(UUID sender, KitType kit, long expiresAt) {}

    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, History> history = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public RematchManager(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    public void remember(UUID player, UUID opponent, KitType kit) {
        long ttl = Math.max(1, plugin.getConfig().getLong("settings.rematch-expiry-seconds", 120));
        history.put(player, new History(opponent, kit, System.currentTimeMillis() + ttl * 1000L));
    }

    public boolean send(Player sender) {
        History h = history.get(sender.getUniqueId());
        if (h == null || h.expiresAt() <= System.currentTimeMillis()) return false;
        Player target = plugin.getServer().getPlayer(h.opponent());
        if (target == null || !target.isOnline() || plugin.matchManager().isInMatch(target.getUniqueId())) return false;
        pending.put(target.getUniqueId(), new Pending(sender.getUniqueId(), h.kit(), h.expiresAt()));
        target.sendMessage(plugin.message("rematch-received").replace("<player>", sender.getName()).replace("<kit>", pretty(h.kit())));
        return true;
    }

    public boolean accept(Player target, Player sender) {
        Pending p = pending.get(target.getUniqueId());
        if (p == null || p.expiresAt() <= System.currentTimeMillis() || !p.sender().equals(sender.getUniqueId())) return false;
        pending.remove(target.getUniqueId(), p);
        return plugin.matchManager().startDirect(target, sender, p.kit());
    }

    public void clear() { history.clear(); pending.clear(); }
    private String pretty(KitType k) { return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_', ' '); }
}
