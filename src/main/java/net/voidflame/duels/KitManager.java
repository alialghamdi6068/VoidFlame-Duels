package net.voidflame.duels;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Locale;
import java.util.Map;

public final class KitManager {
    private final VoidFlameDuelsPlugin plugin;

    public KitManager(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    public boolean apply(Player player, KitType kit) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits." + key(kit));
        if (root == null) return false;
        applyArmor(inv, root.getString("armor", "NETHERITE_SET_PROT4"));
        for (Map<?, ?> item : root.getMapList("items")) {
            Object rawSlot = item.get("slot");
            int slot = rawSlot instanceof Number n ? n.intValue() : -1;
            if (slot < 0 || slot >= 36) continue;
            Material material = material(item.get("material"));
            if (material == null || material == Material.AIR) continue;
            Object rawAmount = item.get("amount");
            int amount = rawAmount instanceof Number n ? Math.max(1, n.intValue()) : 1;
            ItemStack stack = new ItemStack(material, amount);
            applyPotion(stack, item.get("potion"));
            enchant(stack, item.get("enchantments"));
            inv.setItem(slot, stack);
        }
        Material offhand = material(root.getString("offhand", "AIR"));
        inv.setItemInOffHand(new ItemStack(offhand == null ? Material.AIR : offhand));
        plugin.kitEditorManager().applySavedLayout(player, kit);
        player.updateInventory();
        return true;
    }

    private void applyArmor(PlayerInventory inv, String preset) {
        boolean diamond = preset.toUpperCase(Locale.ROOT).startsWith("DIAMOND");
        Material[] armor = diamond
                ? new Material[]{Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET}
                : new Material[]{Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET};
        int protection = preset.contains("PROT4") ? 4 : preset.contains("PROT3") ? 3 : 2;
        ItemStack[] contents = new ItemStack[4];
        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = new ItemStack(armor[i]);
            stack.addUnsafeEnchantment(Enchantment.PROTECTION, protection);
            stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            contents[i] = stack;
        }
        inv.setArmorContents(contents);
    }

    private void applyPotion(ItemStack stack, Object raw) {
        if (!(raw instanceof String value) || !(stack.getItemMeta() instanceof PotionMeta meta)) return;
        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
        boolean upgraded = normalized.endsWith("_2") || normalized.endsWith("_II");
        normalized = normalized.replace("_2", "").replace("_II", "");
        try {
            PotionType type = PotionType.valueOf(normalized);
            meta.setBasePotionType(type);
            if (upgraded) {
                meta.setBasePotionType(type);
                try {
                    meta.setBasePotionType(PotionType.valueOf(normalized + "_STRONG"));
                } catch (IllegalArgumentException ignored) {
                    // Some Paper versions encode upgraded potion variants differently.
                }
            }
            stack.setItemMeta(meta);
        } catch (IllegalArgumentException ignored) {
            // Invalid configured potion leaves the item as a normal potion.
        }
    }

    private void enchant(ItemStack stack, Object raw) {
        if (!(raw instanceof java.util.List<?> list)) return;
        for (Object entry : list) {
            String[] parts = String.valueOf(entry).split(":", 2);
            if (parts.length != 2) continue;
            Enchantment enchantment = Enchantment.getByName(parts[0].toUpperCase(Locale.ROOT));
            if (enchantment == null) continue;
            try { stack.addUnsafeEnchantment(enchantment, Integer.parseInt(parts[1])); }
            catch (NumberFormatException ignored) {}
        }
    }

    private Material material(Object raw) {
        if (raw == null) return Material.AIR;
        try { return Material.valueOf(String.valueOf(raw).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String key(KitType kit) { return kit.name().toLowerCase(Locale.ROOT); }
}
