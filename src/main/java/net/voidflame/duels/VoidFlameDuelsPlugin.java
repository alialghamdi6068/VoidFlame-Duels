package net.voidflame.duels;

import org.bukkit.plugin.java.JavaPlugin;

public final class VoidFlameDuelsPlugin extends JavaPlugin {
    private CoreServices coreServices;
    private QueueManager queueManager;
    private MatchManager matchManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        coreServices = CoreServices.connect(getServer().getServicesManager());
        if (coreServices == null) {
            getLogger().severe("VoidFlame-Core is installed but its service registry is unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        queueManager = new QueueManager(this);
        matchManager = new MatchManager(this, queueManager);

        coreServices.register(QueueManager.class, queueManager);
        coreServices.register(MatchManager.class, matchManager);

        getServer().getPluginManager().registerEvents(queueManager, this);
        getServer().getPluginManager().registerEvents(matchManager, this);
        getLogger().info("VoidFlame-Duels enabled.");
    }

    @Override
    public void onDisable() {
        if (coreServices != null) {
            coreServices.unregister(QueueManager.class);
            coreServices.unregister(MatchManager.class);
        }
        if (matchManager != null) matchManager.shutdown();
        if (queueManager != null) queueManager.shutdown();
    }

    public QueueManager queueManager() { return queueManager; }
    public MatchManager matchManager() { return matchManager; }
}
