package com.vampirespells.addon.event;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodCastOutcomeTrackerTest {

    @Test
    void keepsNestedOutcomesSeparateEvenForTheSameSpell() {
        BloodCastOutcomeTracker tracker = new BloodCastOutcomeTracker();
        UUID player = UUID.randomUUID();
        ResourceLocation spell = ResourceLocation.parse("irons_spellbooks:raise_dead");

        tracker.record(player, spell, true);
        tracker.record(player, spell, false);

        assertFalse(tracker.consumeDenied(player, spell));
        assertTrue(tracker.consumeDenied(player, spell));
        assertFalse(tracker.consumeDenied(player, spell));
    }

    @Test
    void matchesOutcomesByPlayerAndSpell() {
        BloodCastOutcomeTracker tracker = new BloodCastOutcomeTracker();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        ResourceLocation firstSpell = ResourceLocation.parse("irons_spellbooks:blood_step");
        ResourceLocation secondSpell = ResourceLocation.parse("irons_spellbooks:devour");

        tracker.record(firstPlayer, firstSpell, true);
        tracker.record(secondPlayer, secondSpell, false);

        assertFalse(tracker.consumeDenied(secondPlayer, secondSpell));
        assertTrue(tracker.consumeDenied(firstPlayer, firstSpell));
    }
}
