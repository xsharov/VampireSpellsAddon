package com.vampirespells.addon.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PersistentBloodDecisionStoreTest {

    private static final ResourceLocation RAISE_DEAD =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "raise_dead");

    @Test
    void survivesPlayerDataSerializationAndIsConsumedOnce() {
        CompoundTag playerData = new CompoundTag();
        CastStateTracker.BloodCastDecision expected =
                new CastStateTracker.BloodCastDecision(true, 7, 0.5d);

        PersistentBloodDecisionStore.store(playerData, RAISE_DEAD, expected, 12_020);
        CompoundTag reloaded = playerData.copy();

        assertEquals(expected, PersistentBloodDecisionStore.remove(reloaded, RAISE_DEAD));
        assertNull(PersistentBloodDecisionStore.remove(reloaded, RAISE_DEAD));
        assertFalse(reloaded.contains("vampire_spells_addon"));
    }

    @Test
    void lifetimeAdvancesOnlyWhenExplicitlyTicked() {
        CompoundTag playerData = new CompoundTag();
        CastStateTracker.BloodCastDecision decision =
                new CastStateTracker.BloodCastDecision(false, 9, 2d);

        PersistentBloodDecisionStore.store(playerData, RAISE_DEAD, decision, 2);
        PersistentBloodDecisionStore.tick(playerData);
        assertEquals(decision, PersistentBloodDecisionStore.remove(playerData.copy(), RAISE_DEAD));

        PersistentBloodDecisionStore.tick(playerData);
        assertNull(PersistentBloodDecisionStore.remove(playerData, RAISE_DEAD));
    }
}
