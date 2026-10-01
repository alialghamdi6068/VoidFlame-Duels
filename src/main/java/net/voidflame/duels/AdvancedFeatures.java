package net.voidflame.duels;

import org.bukkit.Bukkit;
import net.voidflame.core.storage.StorageService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-cutting practice features that belong to the Duels runtime:
 * reports, coins/tags, welcome/death/AutoGG, announcements, FFA/training
 * utilities, GoldenHard state, duel quick-menu and lightweight replay data.
 *
 * Persistent player values use VoidFlame-Core's StorageService, so this
 * module never creates a second database.
 */
public final class AdvancedFeatures implements Listener {
    private static final String COIN_MODULE = "duels.coins";
    private static final String TAG_MODULE = "duels.tags";
    private static final String REPORT_MODULE = "duels.reports";
    private static final String REPLAY_MODULE = "duels.replays";

    private final VoidFlameDuelsPlugin plugin;
    private final StorageService storage;
    private final Map<UUID, Long> reportCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chatCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> coins = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatTags = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> trainingSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ArmorStand>> trainingDummies = new ConcurrentHashMap<>();
    private final Set<UUID> goldenHard = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<String>> recentCombat = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> replayMatchByPlayer = new ConcurrentHashMap<>();
    private final List<String> announcements = new ArrayList<>();
    private int announcementIndex;
    private int announcementTask = -1;

    public AdvancedFeatures(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        var registration = Bukkit.getServicesManager().getRegistration(StorageService.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("VoidFlame-Core StorageService is unavailable.");
        }
        this.storage = registration.getProvider();
        loadAnnouncements();
        long period = Math.max(20L, plugin.getConfig().getLong("announcements.interval-seconds", 90L) * 20L);
        if (plugin.getConfig().getBoolean("announcements.enabled", true)) {
            announcementTask = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::announce, period, period).getTaskId();
        }
    }

    private void storagePut(String module, String key, String value) {
        storage.put(module, key, value);
    }

    private void storageGet(String module, String key, java.util.function.Consumer<String> consumer) {
        storage.get(module, key).thenAccept(consumer);
    }

    private void loadAnnouncements() {
        announcements.clear();
        announcements.addAll(plugin.getConfig().getStringList("announcements.messages"));
        if (announcements.isEmpty()) {
            announcements.add("&bVoidFlame &7» &fUse &e/duels &fto find a match.");
            announcements.add("&bVoidFlame &7» &fReport suspicious players with &e/report <player> <reason>&f.");
            announcements.add("&bVoidFlame &7» &fPractice, improve and compete.");
        }
    }

    private void announce() {
        if (announcements.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        String message = color(announcements.get(announcementIndex++ % announcements.size()));
        Bukkit.broadcastMessage(message);
    }

    public void shutdown() {
        if (announcementTask != -1) plugin.getServer().getScheduler().cancelTask(announcementTask);
        recentCombat.clear();
        coins.clear();
        reportCooldown.clear();
        chatCooldown.clear();
        combatTags.clear();
        goldenHard.clear();
        trainingSessions.clear();
        replayMatchByPlayer.clear();
        trainingDummies.values().forEach(set -> set.forEach(dummy -> { if (dummy != null && !dummy.isDead()) dummy.remove(); }));
        trainingDummies.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String join = plugin.getConfig().getString("welcome.join-message", "");
        if (!join.isBlank()) event.setJoinMessage(color(join.replace("<player>", player.getName())));
        String title = plugin.getConfig().getString("welcome.title", "");
        String subtitle = plugin.getConfig().getString("welcome.subtitle", "");
        if (!title.isBlank() || !subtitle.isBlank()) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendTitle(
                    color(title.replace("<player>", player.getName())),
                    color(subtitle.replace("<player>", player.getName())), 10, 50, 10));
        }
        loadGoldenHard(player);
        loadCoins(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        recentCombat.remove(event.getPlayer().getUniqueId());
        combatTags.remove(event.getPlayer().getUniqueId());
        UUID matchId = replayMatchByPlayer.get(event.getPlayer().getUniqueId());
        if (matchId != null) {
            recentCombat.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                    .add(System.currentTimeMillis() + "|QUIT");
        }
        coins.remove(event.getPlayer().getUniqueId());
        finishTraining(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        String message = plugin.getConfig().getString("death-messages.default", "&c<victim> was eliminated.");
        Player killer = victim.getKiller();
        if (killer != null) {
            message = plugin.getConfig().getString("death-messages.player-kill", "&c<victim> &7was defeated by &a<killer>&7.");
        }
        event.setDeathMessage(color(message.replace("<victim>", victim.getName())
                .replace("<killer>", killer == null ? "Unknown" : killer.getName())));
        if (killer != null && plugin.getConfig().getBoolean("autogg.enabled", true)) {
            long delay = Math.max(0L, plugin.getConfig().getLong("autogg.delay-ticks", 10L));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (killer.isOnline()) killer.chat(plugin.getConfig().getString("autogg.message", "gg"));
            }, delay);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (!plugin.getConfig().getBoolean("combat-tag.enabled", true)) return;
        long until = System.currentTimeMillis()
                + Math.max(1L, plugin.getConfig().getLong("combat-tag.duration-seconds", 15L)) * 1000L;
        combatTags.put(victim.getUniqueId(), until);
        combatTags.put(attacker.getUniqueId(), until);
        recentCombat.computeIfAbsent(victim.getUniqueId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(System.currentTimeMillis() + "|" + attacker.getUniqueId() + "|" + event.getFinalDamage());
        List<String> entries = recentCombat.get(victim.getUniqueId());
        while (entries.size() > Math.max(1, plugin.getConfig().getInt("features.replay.max-events-per-match", 2000))) entries.remove(0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long current = combatTags.getOrDefault(victim.getUniqueId(), 0L);
            if (current <= System.currentTimeMillis()) combatTags.remove(victim.getUniqueId(), current);
            current = combatTags.getOrDefault(attacker.getUniqueId(), 0L);
            if (current <= System.currentTimeMillis()) combatTags.remove(attacker.getUniqueId(), current);
        }, Math.max(1L, plugin.getConfig().getLong("combat-tag.duration-seconds", 15L)) * 20L);
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    public boolean report(Player reporter, Player target, String reason) {
        if (reporter.equals(target) || reason.isBlank()) return false;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(1L, plugin.getConfig().getLong("report.cooldown-seconds", 20L)) * 1000L;
        Long previous = reportCooldown.putIfAbsent(reporter.getUniqueId(), now);
        if (previous != null && now - previous < cooldown) {
            reportCooldown.put(reporter.getUniqueId(), previous);
            return false;
        }
        String key = now + "-" + reporter.getUniqueId();
        String value = reporter.getUniqueId() + "|" + reporter.getName() + "|" +
                target.getUniqueId() + "|" + target.getName() + "|" + sanitize(reason);
        storage.database().execute(
                "INSERT INTO reports(report_id, reporter, target, reason, status, staff, timestamp) VALUES (?, ?, ?, ?, 'OPEN', NULL, ?)",
                key, reporter.getUniqueId().toString(), target.getUniqueId().toString(), sanitize(reason), now
        );
        storagePut(REPORT_MODULE, key, value);
        String staffMessage = color(plugin.getConfig().getString("report.staff-message",
                "&c[Report] &f<reporter> &7reported &e<target> &7for: &f<reason>"));
        String output = staffMessage.replace("<reporter>", reporter.getName())
                .replace("<target>", target.getName()).replace("<reason>", sanitize(reason));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("voidflame.duels.report.staff") || online.isOp()) online.sendMessage(output);
        }
        return true;
    }

    public boolean isCombatTagged(UUID player) {
        Long until = combatTags.get(player);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) { combatTags.remove(player, until); return false; }
        return true;
    }

    public long combatTagRemainingSeconds(UUID player) {
        Long until = combatTags.get(player);
        if (until == null) return 0L;
        return Math.max(0L, (until - System.currentTimeMillis() + 999L) / 1000L);
    }

    public void startReplay(UUID matchId, UUID first, UUID second) {
        recentCombat.remove(first);
        recentCombat.remove(second);
        recentCombat.put(first, Collections.synchronizedList(new ArrayList<>()));
        recentCombat.put(second, Collections.synchronizedList(new ArrayList<>()));
        replayMatchByPlayer.put(first, matchId);
        replayMatchByPlayer.put(second, matchId);
        long now = System.currentTimeMillis();
        recentCombat.get(first).add(now + "|MATCH_START|" + second);
        recentCombat.get(second).add(now + "|MATCH_START|" + first);
    }

    public void saveReplay(UUID matchId, UUID first, UUID second, String arena, String kit, long durationMs) {
        if (!plugin.getConfig().getBoolean("features.replay.enabled", true)) return;
        List<String> events = new ArrayList<>();
        List<String> firstEvents = recentCombat.get(first);
        List<String> secondEvents = recentCombat.get(second);
        if (firstEvents != null) synchronized (firstEvents) { events.addAll(firstEvents); }
        if (secondEvents != null) synchronized (secondEvents) { events.addAll(secondEvents); }
        events.sort(Comparator.comparingLong(this::eventTimestamp));
        int max = Math.max(1, plugin.getConfig().getInt("features.replay.max-events-per-match", 2000));
        if (events.size() > max) events = new ArrayList<>(events.subList(events.size() - max, events.size()));
        String payload = "kit=" + kit + "|arena=" + arena + "|duration=" + durationMs + "|events=" + String.join(";", events);
        String players = "["" + first + "","" + second + ""]";
        storage.database().execute(
                "INSERT INTO replays(match_id, players_json, arena, kit, timestamp, replay_data) VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(match_id) DO UPDATE SET players_json=excluded.players_json, arena=excluded.arena, kit=excluded.kit, timestamp=excluded.timestamp, replay_data=excluded.replay_data",
                matchId.toString(), players, arena, kit, System.currentTimeMillis(), payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        recentCombat.remove(first);
        recentCombat.remove(second);
        replayMatchByPlayer.remove(first, matchId);
        replayMatchByPlayer.remove(second, matchId);
    }

    private long eventTimestamp(String event) {
        try { return Long.parseLong(event.substring(0, event.indexOf('|'))); }
        catch (Exception ignored) { return Long.MAX_VALUE; }
    }

    public void saveReplay(Match match) {
        if (!plugin.getConfig().getBoolean("features.replay.enabled", true) || match == null) return;
        int max = Math.max(1, plugin.getConfig().getInt("features.replay.max-events-per-match", 2000));
        List<String> events = new ArrayList<>();
        List<String> first = recentCombat.getOrDefault(match.first(), List.of());
        List<String> second = recentCombat.getOrDefault(match.second(), List.of());
        events.addAll(first);
        events.addAll(second);
        events.sort(String::compareTo);
        if (events.size() > max) events = new ArrayList<>(events.subList(events.size() - max, events.size()));
        String value = "kit=" + match.kit() + "|arena=" + match.arena().name()
                + "|duration=" + match.durationSeconds() + "|events=" + String.join(";", events);
        storagePut(REPLAY_MODULE, match.first() + ":" + match.matchId(), value);
        storagePut(REPLAY_MODULE, match.second() + ":" + match.matchId(), value);
    }

    public void openHistory(Player player) {
        storage.database().query(
                "SELECT data_key,data_value FROM module_data WHERE module=? AND data_key LIKE ? ORDER BY updated_at DESC LIMIT 45",
                "stats", "history:" + player.getUniqueId() + ":%")
            .thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(null, 54, color("&8VoidFlame Match History"));
                int slot = 0;
                for (var row : rows) {
                    if (slot >= 45) break;
                    String value = String.valueOf(row.get("data_value"));
                    String[] parts = value.split("\\|", -1);
                    String outcome = parts.length > 0 ? parts[0] : "MATCH";
                    String kit = parts.length > 1 ? parts[1].replace('_', ' ') : "Unknown";
                    String mode = parts.length > 2 ? parts[2] : "Duel";
                    String arena = parts.length > 3 ? parts[3] : "Unknown";
                    Material icon = outcome.equalsIgnoreCase("WIN") ? Material.EMERALD : outcome.equalsIgnoreCase("LOSS") ? Material.REDSTONE : Material.PAPER;
                    ItemStack item = new ItemStack(icon);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(color((outcome.equalsIgnoreCase("WIN") ? "&a" : outcome.equalsIgnoreCase("LOSS") ? "&c" : "&e") + outcome));
                        meta.setLore(List.of(
                                color("&7Kit: &f" + kit),
                                color("&7Mode: &f" + mode),
                                color("&7Arena: &f" + arena),
                                color("&8Match history")
                        ));
                        item.setItemMeta(meta);
                    }
                    inv.setItem(slot++, item);
                }
                if (rows.isEmpty()) {
                    ItemStack empty = item(Material.BARRIER, "&cNo match history", "&7Complete a duel to see it here.");
                    inv.setItem(22, empty);
                }
                player.openInventory(inv);
            }))
            .exceptionally(error -> {
                plugin.getLogger().warning("Could not load match history: " + error.getMessage());
                return null;
            });
    }

    public void openReports(Player staff) {
        if (!staff.hasPermission("voidflame.report.view") && !staff.isOp()) {
            staff.sendMessage(plugin.message("no-permission"));
            return;
        }
        storage.database().query(
                "SELECT report_id, reporter, target, reason, status, timestamp FROM reports ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'CLAIMED' THEN 1 ELSE 2 END, timestamp DESC LIMIT 45")
            .thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory inv = Bukkit.createInventory(null, 54, color("&8VoidFlame Reports"));
                int slot = 0;
                for (var row : rows) {
                    if (slot >= 45) break;
                    String status = String.valueOf(row.get("status"));
                    String reason = String.valueOf(row.get("reason"));
                    String target = String.valueOf(row.get("target"));
                    Material icon = status.equals("OPEN") ? Material.REDSTONE : status.equals("CLAIMED") ? Material.GOLD_INGOT : Material.EMERALD;
                    inv.setItem(slot++, item(icon, "&cReport &7" + target,
                            "&7Status: &f" + status + " &8| &7" + reason,
                            "&eLeft-click: claim", "&aRight-click: resolve"));
                }
                if (rows.isEmpty()) inv.setItem(22, item(Material.BARRIER, "&aNo reports", "&7There are no stored reports."));
                staff.openInventory(inv);
            }));
    }

    @EventHandler
    public void onReportsClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player staff)) return;
        if (!event.getView().getTitle().equals(color("&8VoidFlame Reports"))) return;
        event.setCancelled(true);
        if (!staff.hasPermission("voidflame.report.handle") && !staff.isOp()) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 45) return;
        storage.database().query(
                "SELECT report_id, status FROM reports ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'CLAIMED' THEN 1 ELSE 2 END, timestamp DESC LIMIT 45")
            .thenAccept(rows -> {
                if (slot >= rows.size()) return;
                String id = String.valueOf(rows.get(slot).get("report_id"));
                String status = String.valueOf(rows.get(slot).get("status"));
                String next = event.isRightClick() ? "RESOLVED" : "CLAIMED";
                if ("RESOLVED".equals(status)) return;
                storage.database().execute("UPDATE reports SET status=?, staff=? WHERE report_id=?",
                        next, staff.getUniqueId().toString(), id);
                Bukkit.getScheduler().runTask(plugin, () -> openReports(staff));
            });
    }

    public void loadReplay(UUID player, UUID matchId, java.util.function.Consumer<String> consumer) {
        storage.database().query(
                "SELECT players_json, replay_data FROM replays WHERE match_id=?",
                matchId.toString())
            .thenAccept(rows -> {
                if (rows.isEmpty()) { consumer.accept(null); return; }
                var row = rows.get(0);
                String players = String.valueOf(row.get("players_json"));
                if (!players.contains(player.toString())) { consumer.accept(null); return; }
                Object raw = row.get("replay_data");
                if (!(raw instanceof byte[] bytes)) { consumer.accept(null); return; }
                consumer.accept(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            })
            .exceptionally(error -> { consumer.accept(null); return null; });
    }

    public int coinBalance(UUID player) { return coins.getOrDefault(player, 0); }

    private void loadCoins(Player player) {
        coins.putIfAbsent(player.getUniqueId(), 0);
        storageGet(COIN_MODULE, player.getUniqueId().toString(), raw -> {
            int value = 0;
            if (raw != null) {
                try { value = Math.max(0, Integer.parseInt(raw)); }
                catch (NumberFormatException ignored) { }
            }
            coins.put(player.getUniqueId(), value);
        });
    }

    public void addCoins(UUID player, int amount) {
        if (amount == 0) return;
        int next = Math.max(0, coins.getOrDefault(player, 0) + amount);
        coins.put(player, next);
        storagePut(COIN_MODULE, player.toString(), String.valueOf(next));
    }

    public void rewardMatch(UUID player, String outcome, boolean party) {
        if (player == null) return;
        String key = "rewards." + (party ? "party." : "duel.") + outcome.toLowerCase(Locale.ROOT);
        if (!plugin.getConfig().getBoolean(key + ".enabled", true)) return;
        int amount = Math.max(0, plugin.getConfig().getInt(key + ".coins", 0));
        if (amount > 0) addCoins(player, amount);
        String message = plugin.getConfig().getString(key + ".message", "");
        if (!message.isBlank()) {
            Player p = Bukkit.getPlayer(player);
            if (p != null && p.isOnline()) p.sendMessage(color(message.replace("<coins>", String.valueOf(amount)).replace("<outcome>", outcome)));
        }
    }

    public boolean takeCoins(UUID player, int amount) {
        if (amount < 0) return false;
        int current = coins.getOrDefault(player, 0);
        if (current < amount) return false;
        int next = current - amount;
        coins.put(player, next);
        storagePut(COIN_MODULE, player.toString(), String.valueOf(next));
        return true;
    }

    private List<Map<?, ?>> shopProducts() {
        return plugin.getConfig().getMapList("shop.products");
    }

    public void openCoinShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8VoidFlame CoinShop"));
        List<Map<?, ?>> catalog = shopProducts();
        for (int i = 0; i < Math.min(45, catalog.size()); i++) {
            Map<?, ?> entry = catalog.get(i);
            String name = String.valueOf(entry.containsKey("name") ? entry.get("name") : "Product");
            int price = Math.max(0, parseInt(entry.get("price"), 0));
            Material material = Material.matchMaterial(String.valueOf(entry.containsKey("material") ? entry.get("material") : "NAME_TAG"));
            if (material == null) material = Material.NAME_TAG;
            String color = String.valueOf(entry.containsKey("color") ? entry.get("color") : "&b");
            inv.setItem(i, item(material, color + name, "&7Cost: &e" + price + " coins"));
        }
        inv.setItem(49, item(Material.GOLD_NUGGET, "&eYour Coins: &f" + coinBalance(player.getUniqueId()), "&7Click a product to purchase."));
        player.openInventory(inv);
    }

    private ItemStack item(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(List.of(color(lore)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        meta.setLore(Arrays.stream(lore).map(this::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(color("&8VoidFlame CoinShop"))) return;
        event.setCancelled(true);
        List<Map<?, ?>> catalog = shopProducts();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= Math.min(45, catalog.size())) return;
        Map<?, ?> entry = catalog.get(slot);
        String tag = String.valueOf(entry.containsKey("id") ? entry.get("id") : entry.containsKey("name") ? entry.get("name") : "product");
        int cost = Math.max(0, parseInt(entry.get("price"), 0));
        if (tag.isBlank() || cost < 0) return;
        storage.database().query(
                "SELECT 1 FROM player_shop_purchases WHERE uuid=? AND product_id=?",
                player.getUniqueId().toString(), tag)
            .thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!rows.isEmpty()) {
                    player.sendMessage(color("&eYou already own this product."));
                    return;
                }
                if (!takeCoins(player.getUniqueId(), cost)) {
                    player.sendMessage(color("&cYou do not have enough coins."));
                    return;
                }
                storage.database().execute(
                        "INSERT INTO player_shop_purchases(uuid, product_id, purchased_at) VALUES (?, ?, ?)",
                        player.getUniqueId().toString(), tag, System.currentTimeMillis());
                storagePut(TAG_MODULE, player.getUniqueId().toString(), tag);
                player.sendMessage(color("&aPurchased &f" + tag + " &afor &e" + cost + " coins&a."));
                player.closeInventory();
            }));
    }

    public void toggleGoldenHard(Player player) {
        if (goldenHard.remove(player.getUniqueId())) {
            player.sendMessage(color("&eGoldenHard disabled."));
            return;
        }
        goldenHard.add(player.getUniqueId());
        player.sendMessage(color("&6GoldenHard enabled. &fYou will keep only one life in the current session."));
    }

    public boolean isGoldenHard(UUID player) { return goldenHard.contains(player); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGoldenHardDeath(PlayerDeathEvent event) {
        if (!goldenHard.contains(event.getEntity().getUniqueId())) return;
        goldenHard.remove(event.getEntity().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getEntity().isOnline()) event.getEntity().kickPlayer(color("&6GoldenHard &7» &fRun ended."));
        });
    }

    public void openPractice(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8TotalPractice"));
        inv.setItem(10, item(Material.FEATHER, "&bMovement", "&7Speed, jumps and strafing drills."));
        inv.setItem(13, item(Material.SLIME_BLOCK, "&aStrafing", "&7Movement timing drill."));
        inv.setItem(16, item(Material.TARGET, "&cAim", "&7Hit the target dummy."));
        player.openInventory(inv);
    }

    @EventHandler
    public void onPracticeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(color("&8TotalPractice"))) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 10) {
            startTraining(player, "movement");
        } else if (event.getRawSlot() == 13) {
            startTraining(player, "strafing");
        } else if (event.getRawSlot() == 16) {
            startTraining(player, "aim");
        }
        player.closeInventory();
    }

    @EventHandler
    public void onQuickDuel(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("duel-menu.right-click-enabled", true)) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player player = event.getPlayer();
        if (player.equals(target) || plugin.matchManager().isInMatch(player.getUniqueId())
                || plugin.matchManager().isInMatch(target.getUniqueId())) return;
        event.setCancelled(true);
        player.sendMessage(color("&bDuel Menu &7» &fOpening a duel request for &e" + target.getName()));
        plugin.requests().send(player, target, KitType.SWORD);
    }

    @EventHandler
    public void onTrimInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("trim-editor.enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.SHEARS) return;
        event.getPlayer().sendMessage(color("&bTrim Editor &7» &fUse the smithing table to apply armor trims. Custom trim GUI is enabled in the next editor layer."));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("chat-rate-limit.enabled", true)) return;
        long now = System.currentTimeMillis();
        long last = chatCooldown.getOrDefault(player.getUniqueId(), 0L);
        long min = Math.max(0L, plugin.getConfig().getLong("chat-rate-limit.minimum-interval-ms", 750L));
        if (now - last < min) {
            event.setCancelled(true);
            player.sendMessage(color("&cPlease slow down."));
            return;
        }
        chatCooldown.put(player.getUniqueId(), now);
    }

    private void startTraining(Player player, String drill) {
        finishTraining(player, false);
        UUID sessionId = UUID.randomUUID();
        UUID uuid = player.getUniqueId();
        trainingSessions.put(uuid, sessionId);
        storage.database().execute(
                "INSERT INTO training_sessions(session_id, uuid, drill, started_at, score) VALUES (?, ?, ?, ?, 0)",
                sessionId.toString(), uuid.toString(), drill, System.currentTimeMillis());
        long duration = Math.max(5L, plugin.getConfig().getLong("training.duration-seconds", 30L));
        if ("movement".equals(drill)) {
            player.setWalkSpeed(0.32f);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, (int)duration * 20, 1));
        } else if ("strafing".equals(drill)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, (int)duration * 20, 1));
        } else if ("aim".equals(drill)) {
            ArmorStand dummy = player.getWorld().spawn(player.getLocation().add(0, 0, 3), ArmorStand.class);
            dummy.setInvisible(false);
            dummy.setInvulnerable(false);
            dummy.setCustomName(color("&cTraining Target"));
            dummy.setCustomNameVisible(true);
            dummy.setPersistent(false);
            trainingDummies.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(dummy);
        }
        player.sendMessage(color("&a" + drill.substring(0, 1).toUpperCase(Locale.ROOT) + drill.substring(1) + " drill started."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishTraining(player, true), duration * 20L);
    }

    private void finishTraining(Player player, boolean completed) {
        UUID uuid = player.getUniqueId();
        UUID sessionId = trainingSessions.remove(uuid);
        if (sessionId != null) {
            storage.database().execute(
                    "UPDATE training_sessions SET finished_at=? WHERE session_id=?",
                    System.currentTimeMillis(), sessionId.toString());
            if (completed && player.isOnline()) player.sendMessage(color("&aTraining drill completed."));
        }
        Set<ArmorStand> dummies = trainingDummies.remove(uuid);
        if (dummies != null) dummies.forEach(dummy -> {
            if (dummy != null && !dummy.isDead()) dummy.remove();
        });
        if (player.isOnline()) player.setWalkSpeed(0.2f);
    }

    private void loadGoldenHard(Player player) {
        // Session state is intentionally reset on restart; persistent progression belongs in Stats.
    }

    private String sanitize(String value) {
        return value.replace("|", "/").replace("\n", " ").replace("\r", " ").trim();
    }

    private int parseInt(Object value, int fallback) {
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
    }

    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }

}
