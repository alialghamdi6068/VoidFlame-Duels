package net.voidflame.duels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KitTypeTest {
    @Test
    void parsesHumanReadableKitNames() {
        assertEquals(KitType.SPEAR_MACE, KitType.fromConfig("Spear & Mace"));
        assertEquals(KitType.SWORD, KitType.fromConfig(" Sword "));
        assertEquals(KitType.DIAMOND_SMP, KitType.fromConfig("Diamond SMP"));
        assertEquals(KitType.TNT_MINECART_LT, KitType.fromConfig("TNT Minecart LT"));
        assertEquals(KitType.TNT_MINECART_HT, KitType.fromConfig("TNT Minecart HT"));
    }
}
