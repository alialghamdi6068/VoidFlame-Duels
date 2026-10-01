package net.voidflame.duels;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;

public final class VoidFlameDuelsPlugin extends JavaPlugin implements Listener {
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
    private AdvancedFeatures advancedFeatures;
    private LobbyItemsManager lobbyItemsManager;
    private PlayerSettings playerSettings;
    private CombatTagManager combatTagManager;
    private MatchExploitGuard matchExploitGuard;
    private FfaManager ffaManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        coreServices = CoreServices.connect(getServer().getServicesManager());
        if (coreServices == null) {
            getLogger().severe("VoidFlame-Core service registry is unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            playerSettings = new PlayerSettings();
            arenaManager = new ArenaManager(this);
        } catch (IllegalStateException ex) {
            getLogger().severe(ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
        advancedFeatures = new AdvancedFeatures(this);
        lobbyItemsManager = new LobbyItemsManager(this);
        combatTagManager = new CombatTagManager(this);
        matchExploitGuard = new MatchExploitGuard(this);
        ffaManager = new FfaManager(this);

        coreServices.register(QueueManager.class, queueManager);
        coreServices.register(MatchManager.class, matchManager);
        coreServices.register(KitManager.class, kitManager);
        coreServices.register(PartyManager.class, partyManager);
        coreServices.register(KitEditorManager.class, kitEditorManager);

        registerPublicServices();

        getServer().getPluginManager().registerEvents(queueManager, this);
        getServer().getPluginManager().registerEvents(matchManager, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(spectatorManager, this);
        getServer().getPluginManager().registerEvents(partyManager, this);
        getServer().getPluginManager().registerEvents(kitEditorManager, this);
        getServer().getPluginManager().registerEvents(advancedFeatures, this);
        getServer().getPluginManager().registerEvents(lobbyItemsManager, this);
        getServer().getPluginManager().registerEvents(combatTagManager, this);
        getServer().getPluginManager().registerEvents(matchExploitGuard, this);
        getServer().getPluginManager().registerEvents(ffaManager, this);
        getServer().getPluginManager().registerEvents(new PlayerSettingsListener(this), this);

        getServer().getScheduler().runTaskTimer(this, scoreboardManager::updateAll, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, partyManager::expireInvites, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, partyManager::processQueue, 20L, 20L);
        getServer().getScheduler().runTask(this, scoreboardManager::updateAll);

        DuelCommand duelCommand = new DuelCommand(this);
        register("duel", duelCommand);
        register("queue", duelCommand);
        register("rematch", duelCommand);
        register("rejoin", duelCommand);
        register("duels", duelCommand);
        register("spectate", duelCommand);
        register("kiteditor", duelCommand);

        AdvancedCommand advancedCommand = new AdvancedCommand(this);
        registerAdvanced("report", advancedCommand);
        registerAdvanced("coinshop", advancedCommand);
        registerAdvanced("coins", advancedCommand);
        registerAdvanced("replay", advancedCommand);
        registerAdvanced("practice", advancedCommand);
        registerAdvanced("totalpractice", advancedCommand);
        registerAdvanced("goldenhard", advancedCommand);
        registerAdvanced("ffa", advancedCommand);

        PartyCommand partyCommand = new PartyCommand(this);
        registerParty("party", partyCommand);

        getLogger().info("VoidFlame-Duels enabled with 8 ladders and external arena service.");
    }

    @EventHandler
    public void onCommandAlias(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (!raw.startsWith("/") || !(event.getPlayer() instanceof Player)) return;
        String[] parts = raw.substring(1).trim().split(" ");
        if (parts.length == 0) return;
        String name = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (!name.equals("accept") && !name.equals("deny") && !name.equals("leave")) return;
        event.setCancelled(true);
        new DuelCommand(this).onCommand(event.getPlayer(), new org.bukkit.command.Command(name) {
            @Override public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) { return false; }
        }, name, java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }

    private void registerPublicServices() {
        getServer().getServicesManager().register(QueueManager.class, queueManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(MatchManager.class, matchManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(KitManager.class, kitManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PartyManager.class, partyManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(KitEditorManager.class, kitEditorManager, this, ServicePriority.Normal);
    }

    private void unregisterPublicServices() {
        if (queueManager != null) getServer().getServicesManager().unregister(QueueManager.class, queueManager);
        if (matchManager != null) getServer().getServicesManager().unregister(MatchManager.class, matchManager);
        if (kitManager != null) getServer().getServicesManager().unregister(KitManager.class, kitManager);
        if (partyManager != null) getServer().getServicesManager().unregister(PartyManager.class, partyManager);
        if (kitEditorManager != null) getServer().getServicesManager().unregister(KitEditorManager.class, kitEditorManager);
    }

    private void register(String name, DuelCommand executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    private void registerAdvanced(String name, AdvancedCommand executor) {
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
        if (advancedFeatures != null) advancedFeatures.shutdown();
        if (kitEditorManager != null) kitEditorManager.shutdown();
        if (matchManager != null) matchManager.shutdown();
        if (spectatorManager != null) spectatorManager.shutdown();
        if (requests != null) requests.clear();
        if (rematches != null) rematches.clear();
        if (partyManager != null) partyManager.shutdown();
        if (queueManager != null) queueManager.shutdown();
        if (combatTagManager != null) combatTagManager.clearAll();
        if (ffaManager != null) ffaManager.shutdown();
        unregisterPublicServices();

        if (coreServices != null) {
            coreServices.unregister(QueueManager.class);
            coreServices.unregister(MatchManager.class);
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
    public AdvancedFeatures advancedFeatures() { return advancedFeatures; }
    public LobbyItemsManager lobbyItemsManager() { return lobbyItemsManager; }
    public PlayerSettings playerSettings() { return playerSettings; }
    public CombatTagManager combatTagManager() { return combatTagManager; }
    public FfaManager ffaManager() { return ffaManager; }
}
