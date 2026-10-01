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

import java.util.List;
import java.util.Locale;

public final class DuelMenu implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    public DuelMenu(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    public void open(Player player) {
        int rows = Math.max(3, Math.min(3, plugin.getConfig().getInt("gui.rows", 3)));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                color(plugin.getConfig().getString("gui.title", "&8VoidFlame &7• &fQueue")));

        ItemStack border = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == 2 || col == 0 || col == 8) inv.setItem(slot, border.clone());
        }
        for (int slot : new int[]{1,2,3,5,6,7,19,20,21,23,24,25}) inv.setItem(slot, accent.clone());

        inv.setItem(4, item(Material.NETHER_STAR, color("&d&lQUEUE"),
                color("&7Choose your kit and queue mode."),
                color("&fLeft-click &8» &aUnranked"),
                color("&fRight-click &8» &dRanked")));

        int[] fallbackSlots = {10,11,12,13,14,15,16,17};
        int fallbackIndex = 0;
        for (KitType kit : KitType.values()) {
            String key = kit.name().toLowerCase(Locale.ROOT);
            var sec = plugin.getConfig().getConfigurationSection("gui.kits." + key);
            if (sec == null || !sec.getBoolean("enabled", true)) continue;

            int configured = sec.getInt("slot", -1);
            int slot = configured >= 0 && configured < inv.getSize() ? configured
                    : (fallbackIndex < fallbackSlots.length ? fallbackSlots[fallbackIndex] : -1);
            fallbackIndex++;
            if (slot < 0 || slot >= inv.getSize()) continue;

            Material icon;
            try { icon = Material.valueOf(sec.getString("icon", "STONE").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { icon = Material.STONE; }

            String display = sec.getString("display-name", kit.name());
            List<String> lore = List.of(
                    color("&7" + pretty(kit) + " practice"),
                    "",
                    color("&fUnranked: &a" + plugin.queueManager().queued(kit, false)),
                    color("&fRanked: &d" + plugin.queueManager().queued(kit, true)),
                    color("&fIn Match: &e" + plugin.matchManager().playersInMatches(kit)),
                    "",
                    color("&aLeft-click &8» &fUnranked"),
                    color("&dRight-click &8» &fRanked")
            );
            ItemStack stack = new ItemStack(icon);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(display));
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            inv.setItem(slot, stack);
        }

        inv.setItem(18, item(Material.ARROW, "&7&lBack", "&7Return to the main menu."));
        inv.setItem(26, item(Material.BARRIER, "&c&lClose", "&7Close this menu."));
        player.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = color(plugin.getConfig().getString("gui.title", "&8VoidFlame &7• &fQueue"));
        if (!e.getView().getTitle().equals(title)) return;
        e.setCancelled(true);
        if (e.getRawSlot() == 26) { p.closeInventory(); return; }
        if (e.getRawSlot() == 18) { p.closeInventory(); Bukkit.dispatchCommand(p, "menu"); return; }

        for (KitType kit : KitType.values()) {
            String key = kit.name().toLowerCase(Locale.ROOT);
            var sec = plugin.getConfig().getConfigurationSection("gui.kits." + key);
            if (sec == null || sec.getInt("slot", -1) != e.getRawSlot()) continue;
            boolean ranked = e.isRightClick();
            if (plugin.queueManager().join(p, kit, ranked)) {
                p.sendMessage(plugin.message("joined-queue")
                        .replace("<kit>", pretty(kit))
                        .replace("<type>", ranked ? "Ranked" : "Unranked"));
            } else {
                p.sendMessage(plugin.message("already-queued"));
            }
            p.closeInventory();
            return;
        }
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(List.of(lore));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String pretty(KitType k) {
        return k == KitType.SPEAR_MACE ? "Spear & Mace" : k.name().replace('_', ' ');
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
