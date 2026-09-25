package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class KitEditorManager implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final File file;
    private final YamlConfiguration data;

    public KitEditorManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kit-layouts.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean open(Player player, KitType kit) {
        if (plugin.matchManager().isInMatch(player.getUniqueId())) return false;
        if (sessions.containsKey(player.getUniqueId())) return false;

        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        if (!plugin.kitManager().apply(player, kit)) return false;

        Inventory editor = Bukkit.createInventory(null, 45,
                color("&8Kit Editor &7• &b" + pretty(kit)));
        ItemStack[] saved = load(kit, player.getUniqueId());
        ItemStack[] source = saved == null ? player.getInventory().getContents() : saved;
        for (int i = 0; i < 36 && i < source.length; i++) {
            ItemStack item = source[i];
            if (item != null) editor.setItem(i, item.clone());
        }
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 36; i < editor.getSize(); i++) editor.setItem(i, filler);

        sessions.put(player.getUniqueId(), new Session(kit, snapshot, editor));
        player.openInventory(editor);
        player.sendMessage(plugin.message("kit-editor-opened").replace("<kit>", pretty(kit)));
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.remove(player.getUniqueId());
        if (session == null || event.getInventory() != session.inventory()) return;

        ItemStack[] layout = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack item = event.getInventory().getItem(i);
            layout[i] = item == null ? null : item.clone();
        }
        save(session.kit(), player.getUniqueId(), layout);
        session.snapshot().restore(player);
        player.sendMessage(plugin.message("kit-editor-saved").replace("<kit>", pretty(session.kit())));
    }

    public boolean applySavedLayout(Player player, KitType kit) {
        ItemStack[] saved = load(kit, player.getUniqueId());
        if (saved == null) return false;
        ItemStack[] current = player.getInventory().getContents();
        for (int i = 0; i < 36; i++) current[i] = i < saved.length && saved[i] != null ? saved[i].clone() : null;
        player.getInventory().setContents(current);
        player.updateInventory();
        return true;
    }

    private ItemStack[] load(KitType kit, UUID player) {
        List<?> raw = data.getList(path(kit, player));
        if (raw == null || raw.size() != 36) return null;
        ItemStack[] result = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            Object value = raw.get(i);
            result[i] = value instanceof ItemStack item ? item.clone() : null;
        }
        return result;
    }

    private void save(KitType kit, UUID player, ItemStack[] items) {
        List<ItemStack> list = new ArrayList<>(36);
        for (ItemStack item : items) list.add(item == null ? null : item.clone());
        data.set(path(kit, player), list);
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder.");
                return;
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save kit layouts.", e);
        }
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
