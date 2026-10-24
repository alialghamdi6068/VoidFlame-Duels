package net.voidflame.duels;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import net.voidflame.core.storage.StorageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class KitEditorManager implements Listener {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isEditing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorDrop(PlayerDropItemEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorInteract(PlayerInteractEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorPlace(BlockPlaceEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorBreak(BlockBreakEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorConsume(PlayerItemConsumeEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorHeldSlot(PlayerItemHeldEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorSwapHands(PlayerSwapHandItemsEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isEditing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isEditing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditorProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && isEditing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Session session = sessions.remove(id);
        selectedSlots.remove(id);
        String layoutPrefix = "players." + id + ".";
        layoutCache.keySet().removeIf(key -> key.startsWith(layoutPrefix));
        if (session != null) {
            try {
                session.snapshot().restore(event.getPlayer());
            } catch (RuntimeException error) {
                // Retain the snapshot so a later join can retry restoration.
                sessions.putIfAbsent(id, session);
                plugin.getLogger().severe("Could not restore inventory after kit editor disconnect for " + id
                        + "; snapshot retained for retry: " + error.getMessage());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Session pendingRestore = sessions.get(playerId);
        if (pendingRestore != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!event.getPlayer().isOnline()) return;
                try {
                    pendingRestore.snapshot().restore(event.getPlayer());
                    sessions.remove(playerId, pendingRestore);
                } catch (RuntimeException error) {
                    plugin.getLogger().severe("Could not retry kit editor inventory restoration for " + playerId
                            + ": " + error.getMessage());
                }
            });
        }
        for (KitType kit : KitType.values()) {
            String key = path(kit, playerId);
            storage.get("kit-layouts", key).thenAccept(raw -> {
                List<Integer> layout = decode(raw);
                if (layout != null) layoutCache.put(key, layout);
            }).exceptionally(error -> {
                plugin.getLogger().warning("Could not preload kit layout for " + playerId
                        + " (" + kit.name() + "): " + error.getMessage());
                return null;
            });
        }
    }

    public boolean open(Player player, KitType kit) {
        if (player == null || kit == null) return false;
        UUID id = player.getUniqueId();
        if (plugin.matchManager().isInMatch(id)
                || plugin.matchManager().isDisconnected(id) || plugin.queueManager().isQueued(id)
                || plugin.ffaManager().isInFfa(id) || plugin.spectatorManager().isSpectating(id)) return false;
        if (sessions.containsKey(id)) return false;

        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        if (!plugin.kitManager().applyBase(player, kit)) return false;

        Session session = new Session(kit, snapshot);
        sessions.put(id, session);
        tagBaseItems(player);
        storage.get("kit-layouts", path(kit, id)).whenComplete((raw, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (sessions.get(id) != session) return;
                    if (!player.isOnline()) {
                        // Keep the snapshot while offline so the next join can retry
                        // restoration if the quit-time restore failed.
                        return;
                    }
                    if (error != null) {
                        plugin.getLogger().warning("Could not load kit layout for " + id + " (" + kit.name()
                                + "): " + error.getMessage());
                        try {
                            snapshot.restore(player);
                            sessions.remove(id, session);
                            selectedSlots.remove(id);
                        } catch (RuntimeException restoreError) {
                            plugin.getLogger().severe("Could not restore inventory after kit layout load failure for "
                                    + id + "; session retained for recovery: " + restoreError.getMessage());
                        }
                        player.sendMessage(plugin.message("kit-editor-failed"));
                        return;
                    }
                    List<Integer> layout = decode(raw);
                    if (layout != null) layoutCache.put(path(kit, id), layout);
                    if (layout != null && validLayout(layout, player.getInventory().getContents())) {
                        applyLayout(player, layout);
                    }
                    openDialog(player);
                    player.sendMessage(plugin.message("kit-editor-opened").replace("<kit>", pretty(kit)));
                }));
        return true;
    }

    private void openDialog(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        List<ActionButton> actions = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < PLAYER_SLOTS; slot++) {
            ItemStack item = contents[slot];
            String itemName = item == null || item.getType() == Material.AIR ? "Empty" : prettyMaterial(item.getType());
            actions.add(editorButton("Slot " + (slot + 1) + ": " + itemName,
                    "Select this slot. Select another slot to swap items.",
                    "/kiteditor " + session.kit().name().toLowerCase(Locale.ROOT) + " dialog slot " + slot,
                    NamedTextColor.AQUA));
        }
        actions.add(editorButton("Save Layout", "Save this arrangement to your player profile.",
                "/kiteditor " + session.kit().name().toLowerCase(Locale.ROOT) + " dialog save", NamedTextColor.GREEN));
        actions.add(editorButton("Reset Layout", "Reset to the kit's default inventory arrangement.",
                "/kiteditor " + session.kit().name().toLowerCase(Locale.ROOT) + " dialog reset", NamedTextColor.GOLD));
        actions.add(editorButton("Cancel", "Discard changes and restore your previous inventory.",
                "/kiteditor " + session.kit().name().toLowerCase(Locale.ROOT) + " dialog cancel", NamedTextColor.RED));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Kit Layout • " + pretty(session.kit()), NamedTextColor.WHITE))
                        .externalTitle(Component.text("Kit Editor", NamedTextColor.AQUA))
                        .canCloseWithEscape(false)
                        .body(List.of(
                                DialogBody.plainMessage(Component.text("Select one slot, then another slot to swap their items.", NamedTextColor.WHITE), 320),
                                DialogBody.plainMessage(Component.text("The layout is saved only when you choose Save Layout.", NamedTextColor.GRAY), 320)))
                        .build())
                .type(DialogType.multiAction(actions).columns(3).build()));
        player.showDialog(dialog);
    }

    public void dialogAction(Player player, String action, String rawSlot) {
        if (!player.isOnline()) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        switch (action.toLowerCase(Locale.ROOT)) {
            case "slot" -> {
                int slot;
                try { slot = Integer.parseInt(rawSlot); }
                catch (NumberFormatException ex) { player.sendMessage("§cInvalid kit slot."); return; }
                if (slot < 0 || slot >= PLAYER_SLOTS) { player.sendMessage("§cInvalid kit slot."); return; }
                UUID id = player.getUniqueId();
                Integer selected = selectedSlots.get(id);
                if (selected == null) {
                    selectedSlots.put(id, slot);
                    player.sendMessage("§eSelected slot " + (slot + 1) + ". Choose another slot to swap.");
                } else {
                    ItemStack[] contents = player.getInventory().getStorageContents();
                    ItemStack tmp = contents[selected] == null ? null : contents[selected].clone();
                    contents[selected] = contents[slot] == null ? null : contents[slot].clone();
                    contents[slot] = tmp;
                    player.getInventory().setStorageContents(contents);
                    selectedSlots.remove(id);
                }
                openDialog(player);
            }
            case "save" -> {
                List<Integer> layout = captureLayout(player.getInventory());
                if (layout == null) {
                    player.sendMessage(plugin.message("kit-editor-failed"));
                    openDialog(player);
                    return;
                }
                UUID playerId = player.getUniqueId();
                save(session.kit(), playerId, layout).whenComplete((ignored, error) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (error != null) {
                                plugin.getLogger().warning("Could not save kit layout for " + playerId + ": " + error.getMessage());
                                if (sessions.get(playerId) == session) {
                                    player.sendMessage(plugin.message("kit-editor-failed"));
                                    openDialog(player);
                                }
                                return;
                            }
                            if (sessions.get(playerId) != session) return;
                            try {
                                session.snapshot().restore(player);
                                sessions.remove(playerId, session);
                                selectedSlots.remove(playerId);
                                player.sendMessage(plugin.message("kit-editor-saved").replace("<kit>", pretty(session.kit())));
                            } catch (RuntimeException restoreError) {
                                plugin.getLogger().severe("Layout was saved but original player state could not be restored for "
                                        + playerId + "; editor session retained: " + restoreError.getMessage());
                                player.sendMessage(ChatColor.RED
                                        + "Your layout was saved, but your previous player state could not be restored. Please retry or contact staff.");
                                openDialog(player);
                            }
                        }));
            }
            case "reset" -> {
                selectedSlots.remove(player.getUniqueId());
                if (plugin.kitManager().applyBase(player, session.kit())) {
                    tagBaseItems(player);
                    openDialog(player);
                } else {
                    player.sendMessage(plugin.message("kit-editor-failed"));
                }
            }
            case "cancel" -> {
                try {
                    session.snapshot().restore(player);
                    sessions.remove(player.getUniqueId(), session);
                    selectedSlots.remove(player.getUniqueId());
                    player.sendMessage("§7Kit layout changes cancelled.");
                } catch (RuntimeException error) {
                    plugin.getLogger().severe("Could not restore player state while cancelling kit editor for "
                            + player.getUniqueId() + ": " + error.getMessage());
                    player.sendMessage(plugin.message("kit-editor-failed"));
                    openDialog(player);
                }
            }
            default -> openDialog(player);
        }
    }

    private final Map<UUID, Integer> selectedSlots = new ConcurrentHashMap<>();

    private ActionButton editorButton(String label, String tooltip, String command, NamedTextColor color) {
        return ActionButton.create(Component.text(label, color), Component.text(tooltip, NamedTextColor.GRAY),
                180, DialogAction.commandTemplate(command));
    }

    private String prettyMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
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
        // A saved layout only rearranges the 36 storage slots. Using getContents /
        // setContents here can overwrite armor and offhand slots on some implementations.
        ItemStack[] base = player.getInventory().getStorageContents().clone();
        ItemStack[] arranged = new ItemStack[PLAYER_SLOTS];
        for (int destination = 0; destination < PLAYER_SLOTS; destination++) {
            int source = layout.get(destination);
            arranged[destination] = source == -1 ? null : base[source].clone();
        }
        player.getInventory().setStorageContents(arranged);
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
            if (source == -1) continue;
            if (source < 0 || source >= PLAYER_SLOTS || base[source] == null || !seen.add(source)) return false;
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

    private java.util.concurrent.CompletableFuture<Void> save(KitType kit, UUID player, List<Integer> layout) {
        String key = path(kit, player);
        List<Integer> copy = List.copyOf(layout);
        String encoded = copy.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return storage.put("kit-layouts", key, encoded).thenRun(() -> layoutCache.put(key, copy));
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
            if (player == null || !player.isOnline()) continue;
            try {
                entry.getValue().snapshot().restore(player);
                sessions.remove(entry.getKey(), entry.getValue());
            } catch (RuntimeException error) {
                plugin.getLogger().severe("Could not restore kit editor inventory during shutdown for "
                        + entry.getKey() + ": " + error.getMessage());
            }
        }
    }

    private record Session(KitType kit, PlayerSnapshot snapshot) {}
}
