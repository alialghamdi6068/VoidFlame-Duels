package net.voidflame.duels;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public final class PlayerSnapshot {
    private final Location location;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final int totalExp;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean invulnerable;
    private final int fireTicks;
    private final float fallDistance;
    private final List<PotionEffect> effects;

    private PlayerSnapshot(Player p) {
        this.location = p.getLocation().clone();
        this.contents = cloneItems(p.getInventory().getContents());
        this.armor = cloneItems(p.getInventory().getArmorContents());
        this.offhand = cloneItem(p.getInventory().getItemInOffHand());
        this.health = Math.max(0.0, Math.min(p.getHealth(), p.getMaxHealth()));
        this.food = p.getFoodLevel();
        this.saturation = p.getSaturation();
        this.level = p.getLevel();
        this.exp = p.getExp();
        this.totalExp = p.getTotalExperience();
        this.gameMode = p.getGameMode();
        this.allowFlight = p.getAllowFlight();
        this.flying = p.isFlying();
        this.invulnerable = p.isInvulnerable();
        this.fireTicks = p.getFireTicks();
        this.fallDistance = p.getFallDistance();
        this.effects = new ArrayList<>(p.getActivePotionEffects());
    }

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    public void restore(Player player) {
        player.teleport(location);
        player.setGameMode(gameMode);
        player.getInventory().setContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.getInventory().setItemInOffHand(cloneItem(offhand));
        player.setHealth(Math.max(0.1, Math.min(health, player.getMaxHealth())));
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        player.setTotalExperience(totalExp);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setInvulnerable(invulnerable);
        player.setFireTicks(fireTicks);
        player.setFallDistance(fallDistance);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
        player.updateInventory();
    }

    public Location location() { return location.clone(); }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = cloneItem(source[i]);
        return result;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
