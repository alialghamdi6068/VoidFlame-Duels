package net.voidflame.duels;

import net.voidflame.core.api.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoidFlameDuelsPlugin extends JavaPlugin {
    private ServiceRegistry services;
    private QueueManager queueManager;
    private MatchManager matchManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var core = getServer().getServicesManager().load(ServiceRegistry.class);
        if (core == null) {
            getLogger().severe("VoidFlame-Core service registry is unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        services = core;
        queueManager = new QueueManager(this);
        matchManager = new MatchManager(this, queueManager);
        services.register(QueueManager.class, queueManager);
        services.register(MatchManager.class, matchManager);
        getServer().getPluginManager().registerEvents(queueManager, this);
        getServer().getPluginManager().registerEvents(matchManager, this);
        getLogger().info("VoidFlame-Duels enabled.");
    }

    @Override
    public void onDisable() {
        if (services != null) {
            services.unregister(QueueManager.class);
            services.unregister(MatchManager.class);
        }
        if (matchManager != null) matchManager.shutdown();
        if (queueManager != null) queueManager.shutdown();
    }

    public QueueManager queueManager() { return queueManager; }
    public MatchManager matchManager() { return matchManager; }
}
