package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import net.voidflame.core.storage.StorageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class KitEditorManager implements Listener {
    private static final int EDITOR_SIZE = 45;
    private static final int PLAYER_SLOTS = 36;

    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final StorageService storage;
    private final Map<String, List<Integer>> layoutCache = new ConcurrentHashMap<>();
    private final NamespacedKey sourceSlotKey;

    public KitEditorManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        var registration = plugin.getServer().getServicesManager().getRegistration(StorageService.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("VoidFlame-Core storage service is unavailable.");
        }
        this.storage = registration.getProvider();
        this.sourceSlotKey = new NamespacedKey(plugin, "kit-source-slot");
    }

    public boolean open(Player player, KitType kit) {
        UUID id = player.getUniqueId();
        if (plugin.matchManager().isInMatch(id) || plugin.matchManager().isDisconnected(id)) return false;
        if (sessions.containsKey(id)) return false;

        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        if (!plugin.kitManager().applyBase(player, kit)) return false;

        tagBaseItems(player);
        storage.get("kit-layouts", path(kit, id)).thenAccept(raw -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            List<Integer> layout = decode(raw);
            if (layout != null) layoutCache.put(path(kit, id), layout);
            if (layout != null && validLayout(layout, player.getInventory().getContents())) {
                applyLayout(player, layout);
            }
            Inventory editor = Bukkit.createInventory(null, EDITOR_SIZE,
                    color("&8Kit Editor &7• &b" + pretty(kit)));
            copyPlayerContents(editor, player);
            fillLockedRows(editor);
            sessions.put(id, new Session(kit, snapshot, editor));
            player.openInventory(editor);
            player.sendMessage(plugin.message("kit-editor-opened").replace("<kit>", pretty(kit)));
        })).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                snapshot.restore(player);
                player.sendMessage(plugin.message("kit-editor-failed"));
            });
            return null;
        });
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory()) return;

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= PLAYER_SLOTS) return;
        if (event.getClick().isShiftClick() || event.getClick().isKeyboardClick()
                || event.getClick().isCreativeAction()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;

        int slot = event.getRawSlot();
        ItemStack current = session.inventory().getItem(slot);
        ItemStack cursor = event.getCursor();

        if (cursor == null || cursor.getType() == Material.AIR) {
            event.setCursor(current == null ? null : current.clone());
            session.inventory().setItem(slot, null);
        } else {
            event.setCursor(current == null ? null : current.clone());
            session.inventory().setItem(slot, cursor.clone());
        }
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || event.getInventory() != session.inventory()) return;

        sessions.remove(player.getUniqueId(), session);
        List<Integer> layout = captureLayout(event.getInventory());
        if (layout != null) {
            save(session.kit(), player.getUniqueId(), layout);
            var registration = plugin.getServer().getServicesManager().getRegistration(net.voidflame.core.api.AuditLogService.class);
            if (registration != null && registration.getProvider() != null) {
                registration.getProvider().log(player.getUniqueId().toString(), "KIT_LAYOUT_CHANGE",
                        session.kit().name(), "slots=" + layout.size());
            }
            player.sendMessage(plugin.message("kit-editor-saved").replace("<kit>", pretty(session.kit())));
        } else {
            player.sendMessage(plugin.message("kit-editor-failed"));
        }
        session.snapshot().restore(player);
    }

    public boolean applySavedLayout(Player player, KitType kit) {
        List<Integer> layout = layoutCache.get(path(kit, player.getUniqueId()));
        if (layout == null) return false;
        ItemStack[] base = player.getInventory().getContents().clone();
        if (!validLayout(layout, base)) return false;
        applyLayout(player, layout);
        return true;
    }

    private void applyLayout(Player player, List<Integer> layout) {
        ItemStack[] base = player.getInventory().getContents().clone();
        ItemStack[] arranged = new ItemStack[PLAYER_SLOTS];
        for (int destination = 0; destination < PLAYER_SLOTS; destination++) {
            int source = layout.get(destination);
            arranged[destination] = source < 0 ? null : base[source].clone();
        }
        player.getInventory().setContents(arranged);
        player.updateInventory();
    }

    public boolean isEditing(UUID player) {
        return sessions.containsKey(player);
    }

    private void tagBaseItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < PLAYER_SLOTS; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) continue;
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(sourceSlotKey, PersistentDataType.INTEGER, slot);
            item.setItemMeta(meta);
        }
        player.getInventory().setContents(contents);
    }

    private void copyPlayerContents(Inventory editor, Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            if (contents[i] != null) editor.setItem(i, contents[i].clone());
        }
    }

    private void fillLockedRows(Inventory editor) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = PLAYER_SLOTS; i < EDITOR_SIZE; i++) editor.setItem(i, filler.clone());
    }

    private List<Integer> captureLayout(Inventory inventory) {
        List<Integer> result = new ArrayList<>(PLAYER_SLOTS);
        Set<Integer> seen = new HashSet<>();

        for (int slot = 0; slot < PLAYER_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                result.add(-1);
                continue;
            }
            Integer source = item.getItemMeta().getPersistentDataContainer()
                    .get(sourceSlotKey, PersistentDataType.INTEGER);
            if (source == null || !seen.add(source) || source < 0 || source >= PLAYER_SLOTS) return null;
            result.add(source);
        }
        return result;
    }

    private boolean validLayout(List<Integer> layout, ItemStack[] base) {
        if (layout.size() != PLAYER_SLOTS) return false;
        Set<Integer> seen = new HashSet<>();
        for (int source : layout) {
            if (source < 0) continue;
            if (source >= PLAYER_SLOTS || base[source] == null || !seen.add(source)) return false;
        }
        return true;
    }

    private List<Integer> decode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] values = raw.split(",", -1);
        if (values.length != PLAYER_SLOTS) return null;
        List<Integer> result = new ArrayList<>(PLAYER_SLOTS);
        try {
            for (String value : values) result.add(Integer.parseInt(value));
            return result;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void save(KitType kit, UUID player, List<Integer> layout) {
        String key = path(kit, player);
        List<Integer> copy = List.copyOf(layout);
        layoutCache.put(key, copy);
        storage.put("kit-layouts", key, copy.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not save kit layout: " + error.getMessage());
                    return null;
                });
    }

    private String path(KitType kit, UUID player) {
        return "players." + player + "." + kit.name().toLowerCase(Locale.ROOT);
    }

    private String pretty(KitType kit) {
        return kit == KitType.SPEAR_MACE ? "Spear & Mace" : kit.name().replace('_', ' ');
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public void shutdown() {
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) entry.getValue().snapshot().restore(player);
        }
        sessions.clear();
    }

    private record Session(KitType kit, PlayerSnapshot snapshot, Inventory inventory) {}
}
