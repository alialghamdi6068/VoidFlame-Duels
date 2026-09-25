package net.voidflame.duels;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionType;

import java.util.Locale;
import java.util.Map;

public final class KitManager {
    private final VoidFlameDuelsPlugin plugin;

    public KitManager(VoidFlameDuelsPlugin plugin) { this.plugin = plugin; }

    public void apply(Player player, KitType kit) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits." + key(kit));
        if (root == null) return;
        applyArmor(inv, root.getString("armor", "NETHERITE_SET_PROT4"));
        for (Map<?, ?> item : root.getMapList("items")) {
            Object rawSlot = item.get("slot");
            int slot = rawSlot instanceof Number n ? n.intValue() : 0;
            Material material = material(item.get("material"));
            if (material == null) continue;
            Object rawAmount = item.get("amount");
            int amount = rawAmount instanceof Number n ? n.intValue() : 1;
            ItemStack stack = new ItemStack(material, Math.max(1, amount));
            enchant(stack, item.get("enchantments"));
            inv.setItem(slot, stack);
        }
        inv.setItemInOffHand(new ItemStack(material(root.getString("offhand", "AIR"))));
        player.updateInventory();
    }

    private void applyArmor(PlayerInventory inv, String preset) {
        Material[] armor = preset.startsWith("DIAMOND") ?
                new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS} :
                new Material[]{Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = new ItemStack(armor[i]);
            int protection = preset.contains("PROT4") ? 4 : preset.contains("PROT3") ? 3 : 2;
            stack.addEnchantment(Enchantment.PROTECTION, protection);
            stack.addEnchantment(Enchantment.UNBREAKING, 3);
            inv.setArmorContents(new ItemStack[]{inv.getBoots(), inv.getLeggings(), inv.getChestplate(), inv.getHelmet()});
            if (i == 0) inv.setHelmet(stack);
            if (i == 1) inv.setChestplate(stack);
            if (i == 2) inv.setLeggings(stack);
            if (i == 3) inv.setBoots(stack);
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
