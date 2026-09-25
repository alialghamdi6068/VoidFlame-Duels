package net.voidflame.duels;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoidFlameDuelsPlugin extends JavaPlugin {
    private CoreServices coreServices;
    private QueueManager queueManager;
    private MatchManager matchManager;
    private ArenaManager arenaManager;
    private KitManager kitManager;
    private DuelRequestManager requests;
    private RematchManager rematches;
    private DuelMenu menu;
    private ScoreboardManager scoreboardManager;
    private SpectatorManager spectatorManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        coreServices = CoreServices.connect(getServer().getServicesManager());
        if (coreServices == null) {
            getLogger().severe("VoidFlame-Core service registry is unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        arenaManager = new ArenaManager(this);
        kitManager = new KitManager(this);
        requests = new DuelRequestManager(this);
        rematches = new RematchManager(this);
        queueManager = new QueueManager(this);
        matchManager = new MatchManager(this, queueManager, arenaManager, kitManager);
        menu = new DuelMenu(this);
        scoreboardManager = new ScoreboardManager(this);
        spectatorManager = new SpectatorManager(this);

        coreServices.register(QueueManager.class, queueManager);
        coreServices.register(MatchManager.class, matchManager);
        coreServices.register(ArenaManager.class, arenaManager);
        coreServices.register(KitManager.class, kitManager);

        getServer().getPluginManager().registerEvents(queueManager, this);
        getServer().getPluginManager().registerEvents(matchManager, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(spectatorManager, this);
        getServer().getScheduler().runTaskTimer(this, scoreboardManager::updateAll, 20L, 20L);
        getServer().getScheduler().runTask(this, scoreboardManager::updateAll);

        DuelCommand command = new DuelCommand(this);
        register("duel", command);
        register("queue", command);
        register("rematch", command);
        register("rejoin", command);
        register("duels", command);

        getLogger().info("VoidFlame-Duels enabled with 7 ladders.");
    }

    private void register(String name, DuelCommand executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (matchManager != null) matchManager.shutdown();
        if (spectatorManager != null) spectatorManager.shutdown();
        if (requests != null) requests.clear();
        if (rematches != null) rematches.clear();
        if (queueManager != null) queueManager.shutdown();
        if (coreServices != null) {
            coreServices.unregister(QueueManager.class);
            coreServices.unregister(MatchManager.class);
            coreServices.unregister(ArenaManager.class);
            coreServices.unregister(KitManager.class);
        }
    }

    public String message(String key) {
        String raw = getConfig().getString("messages." + key, "&cMessage not configured.");
        return ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("messages.prefix", "") + raw);
    }

    public QueueManager queueManager() { return queueManager; }
    public MatchManager matchManager() { return matchManager; }
    public ArenaManager arenaManager() { return arenaManager; }
    public KitManager kitManager() { return kitManager; }
    public DuelRequestManager requests() { return requests; }
    public RematchManager rematches() { return rematches; }
    public DuelMenu menu() { return menu; }
    public ScoreboardManager scoreboardManager() { return scoreboardManager; }
    public SpectatorManager spectatorManager() { return spectatorManager; }
}
