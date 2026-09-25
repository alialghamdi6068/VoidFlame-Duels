package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public final class DuelMenu implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    public DuelMenu(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    public void open(Player player) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("gui.rows", 6)));
        Inventory inv = Bukkit.createInventory(null, rows * 9, color(plugin.getConfig().getString("gui.title", "&8⚔ Join Queue")));
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        for (KitType kit : KitType.values()) {
            String key = kit.name().toLowerCase(Locale.ROOT);
            var sec = plugin.getConfig().getConfigurationSection("gui.kits." + key);
            if (sec == null || !sec.getBoolean("enabled", true)) continue;
            int slot = sec.getInt("slot", -1);
            if (slot < 0 || slot >= inv.getSize()) continue;
            Material icon;
            try { icon = Material.valueOf(sec.getString("icon", "STONE").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { icon = Material.STONE; }
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color(sec.getString("display-name", kit.name())));
            java.util.List<String> lore = sec.getStringList("lore").stream()
                    .map(s -> color(s.replace("<queued_players>", String.valueOf(plugin.queueManager().queued(kit)))
                            .replace("<in_match_players>", String.valueOf(plugin.matchManager().playersInMatches(kit)))))
                    .toList();
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = color(plugin.getConfig().getString("gui.title", "&8⚔ Join Queue"));
        if (!e.getView().getTitle().equals(title)) return;
        e.setCancelled(true);
        for (KitType kit : KitType.values()) {
            String key = kit.name().toLowerCase(Locale.ROOT);
            var sec = plugin.getConfig().getConfigurationSection("gui.kits." + key);
            if (sec != null && sec.getInt("slot", -1) == e.getRawSlot()) {
                if (plugin.queueManager().join(p, kit)) p.sendMessage(plugin.message("joined-queue").replace("<kit>", pretty(kit)));
                else p.sendMessage(plugin.message("already-queued"));
                p.closeInventory();
                return;
            }
        }
    }

    private String pretty(KitType k) { return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_', ' '); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}
