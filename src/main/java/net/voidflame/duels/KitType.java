package net.voidflame.duels;

import java.util.Locale;

public enum KitType {
    SWORD, AXE, UHC, MACE, SPEAR_MACE, CRYSTAL, NETHERITE_OP;

    public static KitType fromConfig(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
    }
}
