package net.voidflame.duels;

import java.util.Locale;

public enum KitType {
    SWORD, AXE, UHC, MACE, SPEAR_MACE, CRYSTAL, NETHERITE_OP;

    public static KitType fromConfig(String value) {
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('&', ' ')
                .replace('-', '_')
                .replace(' ', '_');
        return valueOf(normalized.replaceAll("_+", "_"));
    }
}
