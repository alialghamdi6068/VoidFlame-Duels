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
    private PartyManager partyManager;
    private KitEditorManager kitEditorManager;

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
        partyManager = new PartyManager(this);
        kitEditorManager = new KitEditorManager(this);

        coreServices.register(QueueManager.class, queueManager);
        coreServices.register(MatchManager.class, matchManager);
        coreServices.register(ArenaManager.class, arenaManager);
        coreServices.register(KitManager.class, kitManager);
        coreServices.register(PartyManager.class, partyManager);
        coreServices.register(KitEditorManager.class, kitEditorManager);

        getServer().getPluginManager().registerEvents(queueManager, this);
        getServer().getPluginManager().registerEvents(matchManager, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(spectatorManager, this);
        getServer().getPluginManager().registerEvents(partyManager, this);
        getServer().getPluginManager().registerEvents(kitEditorManager, this);

        getServer().getScheduler().runTaskTimer(this, scoreboardManager::updateAll, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, partyManager::expireInvites, 20L, 20L);
        getServer().getScheduler().runTask(this, scoreboardManager::updateAll);

        DuelCommand duelCommand = new DuelCommand(this);
        register("duel", duelCommand);
        register("queue", duelCommand);
        register("rematch", duelCommand);
        register("rejoin", duelCommand);
        register("duels", duelCommand);
        register("spectate", duelCommand);
        register("kiteditor", duelCommand);

        PartyCommand partyCommand = new PartyCommand(this);
        registerParty("party", partyCommand);

        getLogger().info("VoidFlame-Duels enabled with 7 ladders.");
    }

    private void register(String name, DuelCommand executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    private void registerParty(String name, PartyCommand executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (kitEditorManager != null) kitEditorManager.shutdown();
        if (matchManager != null) matchManager.shutdown();
        if (spectatorManager != null) spectatorManager.shutdown();
        if (requests != null) requests.clear();
        if (rematches != null) rematches.clear();
        if (partyManager != null) partyManager.shutdown();
        if (queueManager != null) queueManager.shutdown();
        if (coreServices != null) {
            coreServices.unregister(QueueManager.class);
            coreServices.unregister(MatchManager.class);
            coreServices.unregister(ArenaManager.class);
            coreServices.unregister(KitManager.class);
            coreServices.unregister(PartyManager.class);
            coreServices.unregister(KitEditorManager.class);
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
    public PartyManager partyManager() { return partyManager; }
    public KitEditorManager kitEditorManager() { return kitEditorManager; }
}
