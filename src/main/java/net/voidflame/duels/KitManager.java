package net.voidflame.duels;

import net.voidflame.core.api.KitService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KitManager {
    private final VoidFlameDuelsPlugin plugin;
    private final KitService externalKits;

    public KitManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<KitService> registration =
                Bukkit.getServicesManager().getRegistration(KitService.class);
        this.externalKits = registration == null ? null : registration.getProvider();
    }

    public boolean apply(Player player, KitType kit) {
        String id = kit.name().toLowerCase(Locale.ROOT);
        if (externalKits != null && externalKits.isEnabled(id)) {
            return externalKits.apply(player, id);
        }
        return applyFromConfig(player, kit);
    }

    public boolean applyBase(Player player, KitType kit) {
        return apply(player, kit);
    }

    public boolean openEditor(Player player, KitType kit) {
        if (player == null || kit == null) return false;
        if (externalKits != null) {
            return externalKits.openEditor(player, kit.name().toLowerCase(Locale.ROOT), "default");
        }
        // A local editor is intentionally not faked: without a persistence contract,
        // silently opening a non-saving GUI would be worse than reporting unavailable.
        return false;
    }

    private boolean applyFromConfig(Player player, KitType kit) {
        String path = "kits." + kit.name().toLowerCase(Locale.ROOT);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return false;

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);

        String armor = section.getString("armor", "");
        if (armor != null && !armor.isBlank()) {
            ItemStack[] armorContents = buildArmor(armor);
            player.getInventory().setArmorContents(armorContents);
        }

        List<Map<?, ?>> items = section.getMapList("items");
        for (Map<?, ?> raw : items) {
            int slot = number(raw.get("slot"), -1);
            String materialName = string(raw.get("material"));
            if (slot < 0 || slot >= player.getInventory().getSize() || materialName == null) continue;
            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid kit material '" + materialName + "' in " + path);
                continue;
            }
            int amount = Math.max(1, Math.min(material.getMaxStackSize(), number(raw.get("amount"), 1)));
            ItemStack item = new ItemStack(material, amount);
            applyEnchantments(item, raw.get("enchantments"));
            applyPotion(item, raw.get("potion"));
            player.getInventory().setItem(slot, item);
        }

        String offhand = section.getString("offhand");
        if (offhand != null && !offhand.isBlank()) {
            try {
                player.getInventory().setItemInOffHand(new ItemStack(Material.valueOf(offhand.toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid offhand material '" + offhand + "' in " + path);
            }
        }

        player.updateInventory();
        return true;
    }

    private ItemStack[] buildArmor(String value) {
        Material material;
        int protection = 0;
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("DIAMOND_SET")) material = Material.DIAMOND_HELMET;
        else if (normalized.startsWith("NETHERITE_SET")) material = Material.NETHERITE_HELMET;
        else return new ItemStack[4];

        int marker = normalized.indexOf("PROT");
        if (marker >= 0) {
            try { protection = Integer.parseInt(normalized.substring(marker + 4)); }
            catch (NumberFormatException ignored) {}
        }

        Material[] pieces = {
                material,
                material == Material.DIAMOND_HELMET ? Material.DIAMOND_CHESTPLATE : Material.NETHERITE_CHESTPLATE,
                material == Material.DIAMOND_HELMET ? Material.DIAMOND_LEGGINGS : Material.NETHERITE_LEGGINGS,
                material == Material.DIAMOND_HELMET ? Material.DIAMOND_BOOTS : Material.NETHERITE_BOOTS
        };
        ItemStack[] result = new ItemStack[4];
        for (int i = 0; i < pieces.length; i++) {
            result[i] = new ItemStack(pieces[i]);
            if (protection > 0) {
                var enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
                if (enchantment != null) result[i].addUnsafeEnchantment(enchantment, protection);
            }
        }
        return result;
    }

    private void applyEnchantments(ItemStack item, Object raw) {
        if (!(raw instanceof List<?> list)) return;
        for (Object value : list) {
            if (!(value instanceof String spec)) continue;
            String[] parts = spec.split(":", 2);
            if (parts.length != 2) continue;
            try {
                int level = Integer.parseInt(parts[1]);
                var enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
                if (enchantment != null) item.addUnsafeEnchantment(enchantment, Math.max(1, level));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void applyPotion(ItemStack item, Object raw) {
        if (!(raw instanceof String value) || !(item.getItemMeta() instanceof PotionMeta meta)) return;
        try {
            PotionType type = PotionType.valueOf(value.toUpperCase(Locale.ROOT));
            meta.setBasePotionType(type);
            item.setItemMeta(meta);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid potion type '" + value + "'.");
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
