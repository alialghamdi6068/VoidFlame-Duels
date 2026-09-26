package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PartyMatch {
    private final VoidFlameDuelsPlugin plugin;
    private final MatchManager manager;
    private final Arena arena;
    private final LobbyItemsManager.PartyMode mode;
    private final KitType kit;
    private final List<UUID> players;
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();
    private final Set<UUID> firstTeam = ConcurrentHashMap.newKeySet();
    private final Set<UUID> secondTeam = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private int countdownTask = -1;
    private int limitTask = -1;
    private boolean finished;
    private long startedAt;

    public PartyMatch(VoidFlameDuelsPlugin plugin, MatchManager manager, Arena arena,
                      LobbyItemsManager.PartyMode mode, KitType kit, List<Player> participants) {
        this.plugin = plugin; this.manager = manager; this.arena = arena; this.mode = mode; this.kit = kit;
        List<UUID> shuffled = new ArrayList<>(participants.stream().map(Player::getUniqueId).toList());
        if (mode == LobbyItemsManager.PartyMode.TWO_V_TWO) {
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
        }
        this.players = List.copyOf(shuffled);
        for (Player player : participants) { alive.add(player.getUniqueId()); snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player)); }
        if (mode == LobbyItemsManager.PartyMode.TWO_V_TWO) {
            for (int i = 0; i < players.size(); i++) { if (i < 2) firstTeam.add(players.get(i)); else secondTeam.add(players.get(i)); }
        } else if (mode == LobbyItemsManager.PartyMode.ONE_V_ONE) {
            firstTeam.add(players.get(0)); secondTeam.add(players.get(1));
        }
    }

    public void start() {
        List<Player> online = onlinePlayers();
        if (online.size() != players.size()) { finish(null); return; }
        for (int i = 0; i < online.size(); i++) prepare(online.get(i), spawnFor(i));
        int seconds = Math.max(1, plugin.getConfig().getInt("settings.countdown-seconds", 5));
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = seconds;
            public void run() {
                if (finished) { cancel(); return; }
                if (onlinePlayers().size() != players.size()) { cancel(); finish(null); return; }
                if (left <= 0) {
                    startedAt = System.currentTimeMillis();
                    for (Player player : onlinePlayers()) player.setInvulnerable(false);
                    broadcast(plugin.message("fight")); cancel(); startLimitTimer(); return;
                }
                String title = plugin.getConfig().getString("settings.countdown-title", "&b&l<seconds>").replace("<seconds>", String.valueOf(left));
                for (Player player : onlinePlayers()) player.sendTitle(color(title), "", 0, 20, 0);
                left--;
            }
            private void cancel() { if (countdownTask != -1) Bukkit.getScheduler().cancelTask(countdownTask); countdownTask = -1; }
        }, 0L, 20L).getTaskId();
    }

    private void prepare(Player player, Location spawn) {
        player.closeInventory(); player.teleport(spawn); player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setInvulnerable(true); player.setFireTicks(0); player.setFallDistance(0); player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20); player.setSaturation(20f); plugin.kitManager().apply(player, kit);
    }

    private Location spawnFor(int index) {
        Location anchor = switch (mode) {
            case ONE_V_ONE, TWO_V_TWO -> index < 2 ? arena.spawnA() : arena.spawnB();
            case FFA -> index % 2 == 0 ? arena.spawnA() : arena.spawnB();
        };
        int sideIndex = switch (mode) {
            case ONE_V_ONE -> 0;
            case TWO_V_TWO -> index < 2 ? index : index - 2;
            case FFA -> index / 2;
        };
        double radius = 2.5 + Math.min(4, sideIndex) * 1.2, angle = sideIndex * Math.PI / 2.0;
        return anchor.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    public void handleDeath(PlayerDeathEvent event) {
        if (finished) return;
        UUID id = event.getEntity().getUniqueId();
        if (!alive.remove(id)) return;
        event.setKeepInventory(true); event.getDrops().clear(); event.setDeathMessage(null);
        Bukkit.getScheduler().runTask(plugin, this::checkWinner);
    }

    public void handleQuit(UUID id) { if (players.contains(id) && !finished) { alive.remove(id); checkWinner(); } }

    private void checkWinner() {
        if (mode == LobbyItemsManager.PartyMode.FFA) { if (alive.size() <= 1) finish(alive.stream().findFirst().orElse(null)); return; }
        if (firstTeam.stream().noneMatch(alive::contains)) finish(secondTeam.stream().filter(alive::contains).findFirst().orElse(null));
        else if (secondTeam.stream().noneMatch(alive::contains)) finish(firstTeam.stream().filter(alive::contains).findFirst().orElse(null));
    }

    public void finish(UUID winner) {
        if (finished) return; finished = true;
        if (countdownTask != -1) Bukkit.getScheduler().cancelTask(countdownTask);
        if (limitTask != -1) Bukkit.getScheduler().cancelTask(limitTask);
        for (UUID id : players) {
            PlayerSnapshot snapshot = snapshots.get(id); Player player = Bukkit.getPlayer(id);
            if (snapshot != null && player != null && player.isOnline()) snapshot.restore(player);
            if (player != null && player.isOnline()) player.sendMessage(winner == null ? plugin.message("match-draw") :
                    plugin.message("match-ended").replace("<winner>", name(winner)).replace("<kit>", pretty(kit)));
        }
        var logs = Bukkit.getServicesManager().getRegistration(net.voidflame.core.api.AuditLogService.class);
        if (logs != null && logs.getProvider() != null) {
            logs.getProvider().log(
                    winner == null ? "SYSTEM" : winner.toString(),
                    "PARTY_MATCH_FINISH",
                    arena.name(),
                    "mode=" + mode + "|kit=" + kit + "|players=" + players.size()
            );
        }
        manager.finishParty(this);
    }

    private void startLimitTimer() {
        long limit = plugin.getConfig().getLong("settings.match-time-limit-seconds", 1800);
        if (limit > 0) limitTask = Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!finished) finish(null); }, limit * 20L).getTaskId();
    }
    private List<Player> onlinePlayers() { return players.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).filter(Player::isOnline).toList(); }
    private void broadcast(String message) { onlinePlayers().forEach(p -> p.sendMessage(message)); }
    private String name(UUID id) { Player p=Bukkit.getPlayer(id); String n=p==null?Bukkit.getOfflinePlayer(id).getName():p.getName(); return n==null?"Unknown":n; }
    private String pretty(KitType k) { return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_',' '); }
    private String color(String s) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', s); }
    public boolean contains(UUID id) { return players.contains(id); }
    public boolean finished() { return finished; }
    public List<UUID> players() { return players; }
    public Arena arena() { return arena; }
    public LobbyItemsManager.PartyMode mode() { return mode; }
    public KitType kit() { return kit; }
}