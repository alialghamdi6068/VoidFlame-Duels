package net.voidflame.duels;

import net.voidflame.core.api.KitService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class KitManager {
    private final VoidFlameDuelsPlugin plugin;
    private final KitService kits;

    public KitManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<KitService> registration =
                Bukkit.getServicesManager().getRegistration(KitService.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("VoidFlame-Kits KitService is unavailable.");
        }
        this.kits = registration.getProvider();
    }

    public boolean apply(Player player, KitType kit) {
        return kits.apply(player, kit.name().toLowerCase(java.util.Locale.ROOT));
    }

    public boolean applyBase(Player player, KitType kit) {
        return apply(player, kit);
    }
}
