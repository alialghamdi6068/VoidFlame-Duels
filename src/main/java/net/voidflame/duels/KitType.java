package net.voidflame.duels;

import java.util.Locale;

public enum KitType {
    SWORD, AXE, UHC, MACE, SPEAR_MACE, CRYSTAL, NETHERITE_POT, SMP, DIAMOND_SMP, TNT_MINECART_LT, TNT_MINECART_HIGH_TIER;

    public static KitType fromConfig(String value) {
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('&', ' ')
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.equals("POT") || normalized.equals("NETHERITE_OP") || normalized.equals("NETH_OP")) normalized = "NETHERITE_POT";
        if (normalized.equals("SPEAR_AND_MACE")) normalized = "SPEAR_MACE";
        return valueOf(normalized.replaceAll("_+", "_"));
    }
}
