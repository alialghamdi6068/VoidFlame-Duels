package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class Match {
    private final VoidFlameDuelsPlugin plugin;
    private final MatchManager manager;
    private final UUID first;
    private final UUID second;
    private final KitType kit;
    private final Arena arena;
    private final PlayerSnapshot firstSnapshot;
    private final PlayerSnapshot secondSnapshot;
    private MatchState state = MatchState.COUNTDOWN;
    private long startedAt;
    private int countdownTask = -1;
    private int limitTask = -1;

    public Match(VoidFlameDuelsPlugin plugin, MatchManager manager, UUID first, UUID second, KitType kit, Arena arena,
                 PlayerSnapshot firstSnapshot, PlayerSnapshot secondSnapshot) {
        this.plugin = plugin;
        this.manager = manager;
        this.first = first;
        this.second = second;
        this.kit = kit;
        this.arena = arena;
        this.firstSnapshot = firstSnapshot;
        this.secondSnapshot = secondSnapshot;
    }

    public void start() {
        Player a = Bukkit.getPlayer(first), b = Bukkit.getPlayer(second);
        if (a == null || b == null || !a.isOnline() || !b.isOnline()) {
            finish(a == null ? second : first);
            return;
        }
        prepare(a, arena.spawnA());
        prepare(b, arena.spawnB());
        int seconds = Math.max(1, plugin.getConfig().getInt("settings.countdown-seconds", 5));
        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = seconds;
            @Override public void run() {
                if (state != MatchState.COUNTDOWN) { cancel(); return; }
                Player x = Bukkit.getPlayer(first), y = Bukkit.getPlayer(second);
                if (x == null || y == null || !x.isOnline() || !y.isOnline()) {
                    finish(x == null ? second : first);
                    cancel();
                    return;
                }
                if (left <= 0) {
                    state = MatchState.FIGHTING;
                    startedAt = System.currentTimeMillis();
                    x.setWalkSpeed(0.2f);
                    y.setWalkSpeed(0.2f);
                    x.sendMessage(plugin.message("fight"));
                    y.sendMessage(plugin.message("fight"));
                    cancel();
                    startLimitTimer();
                    return;
                }
                String title = plugin.getConfig().getString("settings.countdown-title", "&b&l<seconds>");
                title = title.replace("<seconds>", String.valueOf(left));
                x.sendTitle(color(title), "", 0, 20, 0);
                y.sendTitle(color(title), "", 0, 20, 0);
                left--;
            }
            private void cancel() {
                if (countdownTask != -1) plugin.getServer().getScheduler().cancelTask(countdownTask);
            }
        }, 0L, 20L).getTaskId();
    }

    private void prepare(Player player, Location spawn) {
        player.closeInventory();
        player.teleport(spawn);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        plugin.kitManager().apply(player, kit);
    }

    private void startLimitTimer() {
        long limit = plugin.getConfig().getLong("settings.match-time-limit-seconds", 1800);
        if (limit <= 0) return;
        limitTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (state == MatchState.FIGHTING) finish(null);
        }, limit * 20L).getTaskId();
    }

    public void finish(UUID winner) {
        if (state == MatchState.FINISHED || state == MatchState.ENDING) return;
        state = MatchState.ENDING;
        cancelTasks();
        Player a = Bukkit.getPlayer(first), b = Bukkit.getPlayer(second);
        if (a != null) a.setInvulnerable(false);
        if (b != null) b.setInvulnerable(false);
        state = MatchState.FINISHED;
        manager.finish(this, winner);
    }

    void restore(Player player) {
        snapshot(player.getUniqueId()).restore(player);
    }

    PlayerSnapshot snapshot(UUID player) {
        if (player.equals(first)) return firstSnapshot;
        if (player.equals(second)) return secondSnapshot;
        throw new IllegalArgumentException("Player is not part of this match");
    }

    private void cancelTasks() {
        if (countdownTask != -1) plugin.getServer().getScheduler().cancelTask(countdownTask);
        if (limitTask != -1) plugin.getServer().getScheduler().cancelTask(limitTask);
        countdownTask = limitTask = -1;
    }

    private String color(String s) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', s); }
    public UUID opponent(UUID player) { return player.equals(first) ? second : first; }
    public Location spawnFor(UUID player) { return player.equals(first) ? arena.spawnA() : arena.spawnB(); }
    public UUID first() { return first; }
    public UUID second() { return second; }
    public KitType kit() { return kit; }
    public Arena arena() { return arena; }
    public MatchState state() { return state; }
    public long durationSeconds() { return startedAt == 0 ? 0 : (System.currentTimeMillis() - startedAt) / 1000; }
}
