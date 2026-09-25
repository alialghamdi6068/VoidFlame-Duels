package net.voidflame.duels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KitTypeTest {
    @Test
    void parsesHumanReadableKitNames() {
        assertEquals(KitType.SPEAR_MACE, KitType.fromConfig("Spear & Mace"));
        assertEquals(KitType.NETHERITE_OP, KitType.fromConfig("netherite-op"));
        assertEquals(KitType.SWORD, KitType.fromConfig(" Sword "));
    }
}
