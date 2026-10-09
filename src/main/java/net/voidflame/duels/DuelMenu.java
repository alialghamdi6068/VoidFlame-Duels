package net.voidflame.duels;

import net.voidflame.core.api.KitService;
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
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Locale;

public final class DuelMenu implements Listener {
    private static final int[] KIT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final VoidFlameDuelsPlugin plugin;

    public DuelMenu(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        int rows = Math.max(5, Math.min(6, plugin.getConfig().getInt("gui.rows", 5)));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                color(plugin.getConfig().getString("gui.title", "&8VoidFlame &7• &fQueue")));

        Material fillerMaterial = material("gui.filler-item", Material.GRAY_STAINED_GLASS_PANE);
        Material accentMaterial = material("gui.accent-item", Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack filler = item(fillerMaterial, " ");
        ItemStack accent = item(accentMaterial, " ");

        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                inv.setItem(slot, filler.clone());
            }
        }
        for (int column : new int[]{1, 2, 3, 5, 6, 7}) {
            inv.setItem(column, accent.clone());
            inv.setItem((rows - 1) * 9 + column, accent.clone());
        }

        inv.setItem(4, item(Material.NETHER_STAR, "&d&lVOIDFLAME QUEUE",
                "&7Choose a kit and queue type.",
                "",
                "&aLeft-click &8» &fUnranked",
                "&dRight-click &8» &fRanked"));

        for (KitType kit : KitType.values()) {
            if (!isKitEnabled(kit)) continue;
            String key = kit.name().toLowerCase(Locale.ROOT);
            var section = plugin.getConfig().getConfigurationSection("gui.kits." + key);
            if (section == null || !section.getBoolean("enabled", true)) continue;

            int slot = getKitSlot(kit, inv.getSize());
            if (slot < 0 || slot >= inv.getSize()) continue;

            Material icon = material("gui.kits." + key + ".icon", Material.STONE);
            String displayName = plugin.getConfig().getString(
                    "gui.kits." + key + ".display-name", "&f" + pretty(kit));

            ItemStack stack = item(icon, displayName,
                    "&7Practice kit: &f" + pretty(kit),
                    "",
                    "&fUnranked queue: &a" + plugin.queueManager().queued(kit, false),
                    "&fRanked queue: &d" + plugin.queueManager().queued(kit, true),
                    "&fPlayers fighting: &e" + plugin.matchManager().playersInMatches(kit),
                    "",
                    "&aLeft-click &8» &fUnranked",
                    "&dRight-click &8» &fRanked");
            inv.setItem(slot, stack);
        }

        int backSlot = inv.getSize() - 8;
        int closeSlot = inv.getSize() - 2;
        inv.setItem(backSlot, item(Material.ARROW, "&b&lBACK", "&7Return to the previous menu."));
        inv.setItem(closeSlot, item(Material.BARRIER, "&c&lCLOSE", "&7Close the queue menu."));
        player.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = color(plugin.getConfig().getString("gui.title", "&8VoidFlame &7• &fQueue"));
        if (!event.getView().getTitle().equals(title)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;

        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() == size - 2) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == size - 8) {
            player.closeInventory();
            return;
        }

        for (KitType kit : KitType.values()) {
            if (!isKitEnabled(kit)) continue;
            String key = kit.name().toLowerCase(Locale.ROOT);
            var section = plugin.getConfig().getConfigurationSection("gui.kits." + key);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            if (getKitSlot(kit, size) != event.getRawSlot()) continue;

            boolean ranked = event.isRightClick();
            if (plugin.queueManager().join(player, kit, ranked)) {
                player.sendMessage(plugin.message("joined-queue")
                        .replace("<kit>", pretty(kit))
                        .replace("<type>", ranked ? "Ranked" : "Unranked"));
            } else {
                player.sendMessage(plugin.message("already-queued"));
            }
            player.closeInventory();
            return;
        }
    }

    private int getKitSlot(KitType target, int inventorySize) {
        String key = target.name().toLowerCase(Locale.ROOT);
        var section = plugin.getConfig().getConfigurationSection("gui.kits." + key);
        if (section != null) {
            int configured = section.getInt("slot", -1);
            if (configured >= 0 && configured < inventorySize
                    && configured % 9 != 0 && configured % 9 != 8
                    && configured / 9 > 0 && configured / 9 < inventorySize / 9 - 1) {
                return configured;
            }
        }

        int index = 0;
        for (KitType kit : KitType.values()) {
            String candidateKey = kit.name().toLowerCase(Locale.ROOT);
            var candidate = plugin.getConfig().getConfigurationSection("gui.kits." + candidateKey);
            if (candidate == null || !candidate.getBoolean("enabled", true) || !isKitEnabled(kit)) continue;
            if (kit == target) return index < KIT_SLOTS.length && KIT_SLOTS[index] < inventorySize - 9
                    ? KIT_SLOTS[index] : -1;
            index++;
        }
        return -1;
    }

    private boolean isKitEnabled(KitType kit) {
        RegisteredServiceProvider<KitService> registration =
                Bukkit.getServicesManager().getRegistration(KitService.class);
        return registration == null || registration.getProvider().isEnabled(
                kit.name().toLowerCase(Locale.ROOT));
    }

    private Material material(String path, Material fallback) {
        String name = plugin.getConfig().getString(path, fallback.name());
        Material parsed = Material.matchMaterial(name == null ? "" : name);
        return parsed == null ? fallback : parsed;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(List.of(lore).stream().map(this::color).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String pretty(KitType kit) {
        return kit == KitType.SPEAR_MACE ? "Spear & Mace" : kit.name().replace('_', ' ');
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
