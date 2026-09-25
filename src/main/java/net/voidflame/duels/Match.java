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
    private MatchState state = MatchState.COUNTDOWN;
    private long startedAt;
    private int taskId = -1;

    public Match(VoidFlameDuelsPlugin plugin, MatchManager manager, UUID first, UUID second, KitType kit, Arena arena) {
        this.plugin = plugin; this.manager = manager; this.first = first; this.second = second; this.kit = kit; this.arena = arena;
    }

    public void start() {
        Player a = Bukkit.getPlayer(first), b = Bukkit.getPlayer(second);
        if (a == null || b == null) { finish(first); return; }
        a.teleport(arena.spawnA());
        b.teleport(arena.spawnB());
        a.setHealth(a.getMaxHealth()); b.setHealth(b.getMaxHealth());
        a.sendMessage(plugin.message("duel-starting"));
        b.sendMessage(plugin.message("duel-starting"));
        int seconds = Math.max(1, plugin.getConfig().getInt("settings.countdown-seconds", 5));
        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = seconds;
            @Override public void run() {
                if (state != MatchState.COUNTDOWN) { cancel(); return; }
                Player x = Bukkit.getPlayer(first), y = Bukkit.getPlayer(second);
                if (x == null || y == null) { finish(x == null ? second : first); cancel(); return; }
                if (left <= 0) {
                    state = MatchState.FIGHTING;
                    startedAt = System.currentTimeMillis();
                    x.sendMessage(plugin.message("fight")); y.sendMessage(plugin.message("fight"));
                    cancel();
                    startLimitTimer();
                    return;
                }
                x.sendTitle("§b" + left, "", 0, 20, 0);
                y.sendTitle("§b" + left, "", 0, 20, 0);
                left--;
            }
            private void cancel() { if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId); }
        }, 0L, 20L).getTaskId();
    }

    private void startLimitTimer() {
        long limit = plugin.getConfig().getLong("settings.match-time-limit-seconds", 1800);
        if (limit <= 0) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (state == MatchState.FIGHTING) finish(first);
        }, limit * 20L);
    }

    public void finish(UUID winner) {
        if (state == MatchState.FINISHED) return;
        state = MatchState.ENDING;
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
        Player a = Bukkit.getPlayer(first), b = Bukkit.getPlayer(second);
        if (a != null) a.setFireTicks(0);
        if (b != null) b.setFireTicks(0);
        state = MatchState.FINISHED;
        manager.finish(this, winner);
    }

    public UUID opponent(UUID player) { return player.equals(first) ? second : first; }
    public Location spawnFor(UUID player) { return player.equals(first) ? arena.spawnA() : arena.spawnB(); }
    public UUID first() { return first; }
    public UUID second() { return second; }
    public KitType kit() { return kit; }
    public Arena arena() { return arena; }
    public MatchState state() { return state; }
    public long durationSeconds() { return startedAt == 0 ? 0 : (System.currentTimeMillis() - startedAt) / 1000; }
}
