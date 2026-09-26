package net.voidflame.duels;

import org.bukkit.Bukkit;
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
    private final Map<UUID, Long> reportCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chatCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> coins = new ConcurrentHashMap<>();
    private final Set<UUID> goldenHard = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<String>> recentCombat = new ConcurrentHashMap<>();
    private final List<String> announcements = new ArrayList<>();
    private int announcementIndex;
    private int announcementTask = -1;

    public AdvancedFeatures(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        loadAnnouncements();
        long period = Math.max(20L, plugin.getConfig().getLong("announcements.interval-seconds", 90L) * 20L);
        if (plugin.getConfig().getBoolean("announcements.enabled", true)) {
            announcementTask = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::announce, period, period).getTaskId();
        }
    }

    private Object storage() {
        try {
            Class<?> type = Class.forName("net.voidflame.core.storage.StorageService");
            var registration = Bukkit.getServicesManager().getRegistration(type);
            return registration == null ? null : registration.getProvider();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private void storagePut(String module, String key, String value) {
        Object service = storage();
        if (service == null) return;
        try {
            service.getClass().getMethod("put", String.class, String.class, String.class)
                    .invoke(service, module, key, value);
        } catch (ReflectiveOperationException ignored) {
            plugin.getLogger().warning("Could not persist Duels data.");
        }
    }

    private void storageGet(String module, String key, java.util.function.Consumer<String> consumer) {
        Object service = storage();
        if (service == null) {
            consumer.accept(null);
            return;
        }
        try {
            Object result = service.getClass().getMethod("get", String.class, String.class)
                    .invoke(service, module, key);
            if (result instanceof java.util.concurrent.CompletableFuture<?> future) {
                future.thenAccept(value -> consumer.accept(value == null ? null : String.valueOf(value)));
            } else {
                consumer.accept(null);
            }
        } catch (ReflectiveOperationException ignored) {
            consumer.accept(null);
        }
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
        goldenHard.clear();
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
        coins.remove(event.getPlayer().getUniqueId());
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
        recentCombat.computeIfAbsent(victim.getUniqueId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(System.currentTimeMillis() + "|" + attacker.getUniqueId() + "|" + event.getFinalDamage());
        List<String> entries = recentCombat.get(victim.getUniqueId());
        while (entries.size() > 200) entries.remove(0);
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

    public boolean takeCoins(UUID player, int amount) {
        if (amount < 0) return false;
        int current = coins.getOrDefault(player, 0);
        if (current < amount) return false;
        int next = current - amount;
        coins.put(player, next);
        storagePut(COIN_MODULE, player.toString(), String.valueOf(next));
        return true;
    }

    public void openCoinShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8VoidFlame CoinShop"));
        String[][] catalog = {
                {"[Void]", "100", "NAME_TAG", "&b"},
                {"[Champion]", "500", "NETHER_STAR", "&d"},
                {"[Duelist]", "750", "DIAMOND_SWORD", "&a"},
                {"[Combo]", "900", "IRON_SWORD", "&e"},
                {"[Clutch]", "1200", "TOTEM_OF_UNDYING", "&6"},
                {"[Warlord]", "1500", "NETHERITE_SWORD", "&c"},
                {"[Unbreakable]", "1750", "NETHERITE_CHESTPLATE", "&5"},
                {"[Speedster]", "2000", "FEATHER", "&b"},
                {"[Striker]", "2250", "ARROW", "&f"},
                {"[Swordsman]", "2500", "DIAMOND_SWORD", "&3"},
                {"[AxeMaster]", "2750", "DIAMOND_AXE", "&6"},
                {"[Crystal]", "3000", "END_CRYSTAL", "&d"},
                {"[Mace]", "3250", "MACE", "&5"},
                {"[Spear]", "3500", "SPEAR", "&a"},
                {"[VoidWalker]", "4000", "ENDER_PEARL", "&8"},
                {"[Nightmare]", "5000", "WITHER_SKELETON_SKULL", "&8"},
                {"[Legend]", "7500", "DRAGON_EGG", "&5"},
                {"[Mythic]", "10000", "DRAGON_HEAD", "&d"}
        };
        for (int i = 0; i < catalog.length; i++) {
            String[] entry = catalog[i];
            inv.setItem(i, item(Material.matchMaterial(entry[2]) == null ? Material.NAME_TAG : Material.matchMaterial(entry[2]),
                    entry[3] + entry[0], "&7Cost: &e" + entry[1] + " coins"));
        }
        inv.setItem(49, item(Material.GOLD_NUGGET, "&eYour Coins: &f" + coinBalance(player.getUniqueId()), "&7Earn coins by playing."));
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

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(color("&8CoinShop"))) return;
        event.setCancelled(true);
        String[][] catalog = {
                {"[Void]", "100"}, {"[Champion]", "500"}, {"[Duelist]", "750"}, {"[Combo]", "900"},
                {"[Clutch]", "1200"}, {"[Warlord]", "1500"}, {"[Unbreakable]", "1750"}, {"[Speedster]", "2000"},
                {"[Striker]", "2250"}, {"[Swordsman]", "2500"}, {"[AxeMaster]", "2750"}, {"[Crystal]", "3000"},
                {"[Mace]", "3250"}, {"[Spear]", "3500"}, {"[VoidWalker]", "4000"}, {"[Nightmare]", "5000"},
                {"[Legend]", "7500"}, {"[Mythic]", "10000"}
        };
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= catalog.length) return;
        String tag = catalog[slot][0];
        int cost;
        try { cost = Integer.parseInt(catalog[slot][1]); } catch (NumberFormatException ex) { return; }
        if (!takeCoins(player.getUniqueId(), cost)) {
            player.sendMessage(color("&cYou do not have enough coins."));
            return;
        }
        storagePut(TAG_MODULE, player.getUniqueId().toString(), tag);
        player.sendMessage(color("&aUnlocked tag &f" + tag + "&a."));
        player.closeInventory();
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
            player.setWalkSpeed(0.32f);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 30, 1));
            player.sendMessage(color("&aMovement drill started."));
        } else if (event.getRawSlot() == 13) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 20 * 30, 1));
            player.sendMessage(color("&aStrafing drill started."));
        } else if (event.getRawSlot() == 16) {
            ArmorStand dummy = player.getWorld().spawn(player.getLocation().add(0, 0, 3), ArmorStand.class);
            dummy.setInvisible(false);
            dummy.setInvulnerable(false);
            dummy.setCustomName(color("&cTraining Target"));
            dummy.setCustomNameVisible(true);
            dummy.setPersistent(false);
            player.sendMessage(color("&aAim target spawned."));
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

    private void loadGoldenHard(Player player) {
        // Session state is intentionally reset on restart; persistent progression belongs in Stats.
    }

    private String sanitize(String value) {
        return value.replace("|", "/").replace("\n", " ").replace("\r", " ").trim();
    }

    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }

}
