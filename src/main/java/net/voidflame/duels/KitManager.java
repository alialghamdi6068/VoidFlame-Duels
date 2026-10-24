package net.voidflame.duels;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KitManager {
    private final VoidFlameDuelsPlugin plugin;
    public KitManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean apply(Player player, KitType kit) {
        boolean applied = applyConfigured(player, kit);
        if (applied && plugin.kitEditorManager() != null) {
            plugin.kitEditorManager().applySavedLayout(player, kit);
        }
        return applied;
    }

    private boolean applyConfigured(Player player, KitType kit) {
        if (player == null || kit == null) return false;
        String id = kit.name().toLowerCase(Locale.ROOT);
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits." + id);
        if (root == null) {
            plugin.getLogger().warning("Cannot apply kit '" + id + "': no kits." + id + " configuration exists.");
            return false;
        }
        ConfigurationSection items = root.getConfigurationSection("items");
        if (items == null || items.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("Cannot apply kit '" + id + "': its items section is missing or empty. Player inventory was left untouched.");
            return false;
        }

        // Fully parse and validate configured items before clearing any player inventory.
        Map<Integer, ItemStack> preparedItems = new LinkedHashMap<>();
        for (String key : items.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException invalidSlot) {
                plugin.getLogger().warning("Ignoring invalid slot '" + key + "' in kit '" + id + "'.");
                continue;
            }
            if (slot < 0 || slot > 40) {
                plugin.getLogger().warning("Ignoring out-of-range slot " + slot + " in kit '" + id + "'.");
                continue;
            }
            try {
                ItemStack item = readItem(items.getConfigurationSection(key));
                if (item != null) preparedItems.put(slot, item);
                else plugin.getLogger().warning("Ignoring invalid item at slot " + slot + " in kit '" + id + "'.");
            } catch (RuntimeException invalidItem) {
                plugin.getLogger().warning("Ignoring malformed item at slot " + slot + " in kit '" + id
                        + "': " + invalidItem.getMessage());
            }
        }

        ItemStack configuredOffhand = null;
        ConfigurationSection offhand = root.getConfigurationSection("offhand");
        if (offhand != null) {
            try {
                configuredOffhand = readItem(offhand);
            } catch (RuntimeException invalidOffhand) {
                plugin.getLogger().warning("Ignoring malformed offhand item in kit '" + id
                        + "': " + invalidOffhand.getMessage());
            }
        }
        if (preparedItems.isEmpty() && configuredOffhand == null) {
            plugin.getLogger().severe("Cannot apply kit '" + id
                    + "': no valid items were parsed. Player inventory was left untouched.");
            return false;
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.setItemOnCursor(new ItemStack(Material.AIR));
        preparedItems.forEach((slot, item) -> setSlot(player, slot, item));
        if (configuredOffhand != null) player.getInventory().setItemInOffHand(configuredOffhand);
        return true;
    }

    public boolean applyBase(Player player, KitType kit) {
        return applyConfigured(player, kit);
    }

    public boolean openEditor(Player player, KitType kit) {
        if (player == null || kit == null || plugin.kitEditorManager() == null) return false;
        return plugin.kitEditorManager().open(player, kit);
    }

    private ItemStack readItem(ConfigurationSection section) {
        if (section == null) return null;
        String materialName = section.getString("material", "AIR");
        Material material = section.getBoolean("golden-head", false)
                ? Material.PLAYER_HEAD : Material.matchMaterial(materialName);
        if (material == null || material == Material.AIR) return null;

        int amount = Math.max(1, Math.min(section.getInt("amount", 1), material.getMaxStackSize()));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null && section.getBoolean("golden-head", false)) {
            meta.setDisplayName("Golden Head");
            item.setItemMeta(meta);
        }

        applyPotion(item, section.getString("potion"));
        applyCustomPotionEffect(item, section.getString("custom-potion-effect"),
                section.getInt("custom-potion-duration-ticks", 0));
        applyChargedProjectile(item, section.getConfigurationSection("charged-projectile"));
        applyEnchantments(item, section.getConfigurationSection("enchants"));
        applyShulkerContents(item, section.getConfigurationSection("contents"));
        return item;
    }

    private void applyPotion(ItemStack item, String potionName) {
        if (potionName == null || !(item.getItemMeta() instanceof PotionMeta meta)) return;
        try {
            meta.setBasePotionType(PotionType.valueOf(potionName.toUpperCase(Locale.ROOT)));
            item.setItemMeta(meta);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid potion type '" + potionName + "'.");
        }
    }

    private void applyCustomPotionEffect(ItemStack item, String effectName, int durationTicks) {
        if (effectName == null || durationTicks <= 0 || !(item.getItemMeta() instanceof PotionMeta meta)) return;
        PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase(Locale.ROOT));
        if (type == null) return;
        meta.addCustomEffect(new PotionEffect(type, durationTicks, 0, false, true, true), true);
        item.setItemMeta(meta);
    }

    private void applyChargedProjectile(ItemStack item, ConfigurationSection section) {
        if (section == null || !(item.getItemMeta() instanceof CrossbowMeta meta)) return;
        ItemStack projectile = readItem(section);
        if (projectile == null) return;
        meta.setChargedProjectiles(List.of(projectile));
        item.setItemMeta(meta);
    }

    private void applyEnchantments(ItemStack item, ConfigurationSection enchants) {
        if (enchants == null) return;
        for (String name : enchants.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByName(name.toUpperCase(Locale.ROOT));
            if (enchantment == null) {
                enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            }
            if (enchantment != null) {
                item.addUnsafeEnchantment(enchantment, Math.max(1, enchants.getInt(name, 1)));
            }
        }
    }

    private void applyShulkerContents(ItemStack item, ConfigurationSection contents) {
        if (contents == null || !(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!(meta.getBlockState() instanceof ShulkerBox shulker)) return;
        for (String key : contents.getKeys(false)) {
            int slot;
            try { slot = Integer.parseInt(key); } catch (NumberFormatException ignored) { continue; }
            if (slot < 0 || slot >= shulker.getInventory().getSize()) continue;
            ItemStack nested = readItem(contents.getConfigurationSection(key));
            if (nested != null) shulker.getInventory().setItem(slot, nested);
        }
        meta.setBlockState(shulker);
        item.setItemMeta(meta);
    }

    private void setSlot(Player player, int slot, ItemStack item) {
        if (slot < 36) {
            player.getInventory().setItem(slot, item);
            return;
        }
        switch (slot) {
            case 36 -> player.getInventory().setBoots(item);
            case 37 -> player.getInventory().setLeggings(item);
            case 38 -> player.getInventory().setChestplate(item);
            case 39 -> player.getInventory().setHelmet(item);
            case 40 -> player.getInventory().setItemInOffHand(item);
            default -> { }
        }
    }
}
