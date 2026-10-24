package net.voidflame.duels;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public final class VoidFlameDuelsPlugin extends JavaPlugin implements Listener {
    private CoreServices coreServices;
    private QueueManager queueManager;
    private MatchManager matchManager;
    private ArenaManager arenaManager;
    private KitManager kitManager;
    private KitEditorManager kitEditorManager;
    private DuelRequestManager requests;
    private RematchManager rematches;
    private DuelMenu menu;
    private NativeDialogMenus nativeMenus;
    private BedrockFormMenus bedrockMenus;
    private SpectatorManager spectatorManager;
    private PartyManager partyManager;
    private AdvancedFeatures advancedFeatures;
    private LobbyItemsManager lobbyItemsManager;
    private PlayerSettings playerSettings;
    private SettingsMenu settingsMenu;
    private CombatTagManager combatTagManager;
    private MatchExploitGuard matchExploitGuard;
    private FfaManager ffaManager;
    private DuelsAdminMenu duelsAdminMenu;

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
        try {
            kitEditorManager = new KitEditorManager(this);
        } catch (IllegalStateException ex) {
            getLogger().severe(ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        requests = new DuelRequestManager(this);
        rematches = new RematchManager(this);
        queueManager = new QueueManager(this);
        matchManager = new MatchManager(this, queueManager, arenaManager, kitManager);
        menu = new DuelMenu(this);
        nativeMenus = new NativeDialogMenus(this);
        bedrockMenus = new BedrockFormMenus(this);
        spectatorManager = new SpectatorManager(this);
        partyManager = new PartyManager(this);
        advancedFeatures = new AdvancedFeatures(this);
        lobbyItemsManager = new LobbyItemsManager(this);
        combatTagManager = new CombatTagManager(this);
        matchExploitGuard = new MatchExploitGuard(this);
        ffaManager = new FfaManager(this);
        settingsMenu = new SettingsMenu(this);
        duelsAdminMenu = new DuelsAdminMenu(this);

        coreServices.register(QueueManager.class, queueManager);
        coreServices.register(MatchManager.class, matchManager);
        coreServices.register(KitManager.class, kitManager);
        coreServices.register(PartyManager.class, partyManager);

        registerPublicServices();

        getServer().getPluginManager().registerEvents(queueManager, this);
        getServer().getPluginManager().registerEvents(matchManager, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(kitEditorManager, this);
        getServer().getPluginManager().registerEvents(spectatorManager, this);
        getServer().getPluginManager().registerEvents(partyManager, this);
        getServer().getPluginManager().registerEvents(advancedFeatures, this);
        getServer().getPluginManager().registerEvents(lobbyItemsManager, this);
        getServer().getPluginManager().registerEvents(settingsMenu, this);
        getServer().getPluginManager().registerEvents(duelsAdminMenu, this);
        getServer().getPluginManager().registerEvents(combatTagManager, this);
        getServer().getPluginManager().registerEvents(matchExploitGuard, this);
        getServer().getPluginManager().registerEvents(ffaManager, this);
        getServer().getPluginManager().registerEvents(new PlayerSettingsListener(this), this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            VoidFlamePlaceholders placeholders = new VoidFlamePlaceholders(this);
            getServer().getPluginManager().registerEvents(placeholders, this);
            placeholders.start();
        }

        getServer().getScheduler().runTaskTimer(this, partyManager::expireInvites, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, partyManager::processQueue, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, requests::expireStale, 20L, 100L);
        getServer().getScheduler().runTaskTimer(this, lobbyItemsManager::enforceDuelItems, 20L, 20L);
        getServer().getScheduler().runTask(this, () -> {});

        DuelCommand duelCommand = new DuelCommand(this);
        register("duel", duelCommand);
        register("queue", duelCommand);
        register("rematch", duelCommand);
        register("rejoin", duelCommand);
        register("duels", duelCommand);
        register("spectate", duelCommand);
        register("kiteditor", duelCommand);
        register("accept", duelCommand);
        register("deny", duelCommand);
        register("leave", duelCommand);
        StatsCommand statsCommand = new StatsCommand(this);
        if (getCommand("stats") != null) {
            getCommand("stats").setExecutor(statsCommand);
            getCommand("stats").setTabCompleter(statsCommand);
        }
        if (getCommand("leaderboard") != null) getCommand("leaderboard").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (JavaAdminAccess.isBedrock(p)) {
                if (bedrockMenus.available()) bedrockMenus.openLeaderboard(p); else p.performCommand("stats top elo");
            } else nativeMenus.openLeaderboard(p);
            return true;
        });
        if (getCommand("vfmenu") != null) getCommand("vfmenu").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (JavaAdminAccess.isBedrock(p)) {
                if (bedrockMenus.available()) bedrockMenus.openMain(p); else menu.open(p);
            } else nativeMenus.openMain(p);
            return true;
        });
        if (getCommand("vfui") != null) getCommand("vfui").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            return nativeMenus.handle(p, args);
        });
        if (getCommand("settings") != null) getCommand("settings").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("toggle")) {
                if (!settingsMenu.toggleByKey(p, args[1])) p.sendMessage(ChatColor.RED + "Unknown setting.");
                return true;
            }
            settingsMenu.open(p);
            return true;
        });
        if (getCommand("duelsadmin") != null) getCommand("duelsadmin").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!JavaAdminAccess.hasAdminAccess(p)) {
                p.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            nativeMenus.openAdmin(p);
            return true;
        });

        ArenaSetupCommand arenaSetup = new ArenaSetupCommand(this);
        if (getCommand("duelarena") != null) {
            getCommand("duelarena").setExecutor(arenaSetup);
            getCommand("duelarena").setTabCompleter(arenaSetup);
        }

        AdvancedCommand advancedCommand = new AdvancedCommand(this);
        registerAdvanced("report", advancedCommand);
        registerAdvanced("reports", advancedCommand);
        registerAdvanced("coinshop", advancedCommand);
        registerAdvanced("coins", advancedCommand);
        registerAdvanced("replay", advancedCommand);
        registerAdvanced("history", advancedCommand);
        registerAdvanced("practice", advancedCommand);
        registerAdvanced("totalpractice", advancedCommand);
        registerAdvanced("goldenhard", advancedCommand);
        registerAdvanced("ffa", advancedCommand);

        PartyCommand partyCommand = new PartyCommand(this);
        registerParty("party", partyCommand);

        getLogger().info("VoidFlame-Duels enabled with 8 ladders and external arena service.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (nativeMenus != null) nativeMenus.clearPlayer(event.getPlayer().getUniqueId());
        if (requests != null) requests.removePlayer(event.getPlayer().getUniqueId());
    }

    /* removed legacy command preprocess alias */
    /* @EventHandler
    public void onCommandAlias(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (!raw.startsWith("/") || !(event.getPlayer() instanceof Player)) return;
        String[] parts = raw.substring(1).trim().split("\\s+");
        if (parts.length == 0) return;
        String name = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (!name.equals("accept") && !name.equals("deny") && !name.equals("leave")) return;
        event.setCancelled(true);
        new DuelCommand(this).onCommand(event.getPlayer(), new org.bukkit.command.Command(name) {
            @Override public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) { return false; }
        }, name, java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }
    */

    private void registerPublicServices() {
        getServer().getServicesManager().register(QueueManager.class, queueManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(MatchManager.class, matchManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(KitManager.class, kitManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PartyManager.class, partyManager, this, ServicePriority.Normal);
    }

    private void unregisterPublicServices() {
        if (queueManager != null) getServer().getServicesManager().unregister(QueueManager.class, queueManager);
        if (matchManager != null) getServer().getServicesManager().unregister(MatchManager.class, matchManager);
        if (kitManager != null) getServer().getServicesManager().unregister(KitManager.class, kitManager);
        if (partyManager != null) getServer().getServicesManager().unregister(PartyManager.class, partyManager);
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
        if (requests != null) requests.clear();
        if (rematches != null) rematches.clear();

        // Restore temporary player states before MatchManager performs its final
        // recovery pass; otherwise failed restores created by these managers would
        // be left behind after MatchManager has already shut down.
        if (kitEditorManager != null) kitEditorManager.shutdown();
        if (spectatorManager != null) spectatorManager.shutdown();
        if (ffaManager != null) ffaManager.shutdown();
        if (combatTagManager != null) combatTagManager.clearAll();

        if (matchManager != null) matchManager.shutdown();
        if (partyManager != null) partyManager.shutdown();
        if (queueManager != null) queueManager.shutdown();
        unregisterPublicServices();

        if (coreServices != null) {
            coreServices.unregister(QueueManager.class);
            coreServices.unregister(MatchManager.class);
            coreServices.unregister(KitManager.class);
            coreServices.unregister(PartyManager.class);
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
    public KitEditorManager kitEditorManager() { return kitEditorManager; }
    public DuelRequestManager requests() { return requests; }
    public RematchManager rematches() { return rematches; }
    public DuelMenu menu() { return menu; }
    public NativeDialogMenus nativeMenus() { return nativeMenus; }

    public BedrockFormMenus bedrockMenus() { return bedrockMenus; }
    public SettingsMenu settingsMenu() { return settingsMenu; }
    public SpectatorManager spectatorManager() { return spectatorManager; }
    public PartyManager partyManager() { return partyManager; }
    public AdvancedFeatures advancedFeatures() { return advancedFeatures; }
    public LobbyItemsManager lobbyItemsManager() { return lobbyItemsManager; }
    public PlayerSettings playerSettings() { return playerSettings; }
    public CombatTagManager combatTagManager() { return combatTagManager; }
    public FfaManager ffaManager() { return ffaManager; }
}
