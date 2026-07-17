package com.vampirespells.addon.mechanics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodMechanicsTest {

    @Test
    void interpolatesBloodCostAndRoundsUp() {
        assertEquals(1, BloodMechanics.calculateBloodCost(20, 20, 140, 0.05, 0.10));
        assertEquals(12, BloodMechanics.calculateBloodCost(100, 20, 140, 0.05, 0.15));
        assertEquals(28, BloodMechanics.calculateBloodCost(140, 20, 140, 0.05, 0.20));
    }

    @Test
    void disablesBloodCostForInvalidOrZeroInputs() {
        assertEquals(0, BloodMechanics.calculateBloodCost(0, 20, 140, 0.05, 0.10));
        assertEquals(0, BloodMechanics.calculateBloodCost(100, 20, 140, 0, 0));
        assertEquals(0, BloodMechanics.calculateBloodCost(100, Double.NaN, 140, Double.NaN, 0));
    }

    @Test
    void selectsBloodForConfiguredOrInsufficientManaPaths() {
        assertTrue(BloodMechanics.shouldUseBlood(true, 100, 25));
        assertTrue(BloodMechanics.shouldUseBlood(false, 24.9f, 25));
        assertFalse(BloodMechanics.shouldUseBlood(false, 25, 25));
        assertFalse(BloodMechanics.shouldUseBlood(false, 0, 0));
        assertTrue(BloodMechanics.shouldUseBlood(false, Float.NaN, 25));
    }

    @Test
    void requiresTheFullAtomicBloodCost() {
        assertTrue(BloodMechanics.canAffordBlood(5, 5));
        assertFalse(BloodMechanics.canAffordBlood(4, 5));
        assertFalse(BloodMechanics.canAffordBlood(5, -1));
    }

    @Test
    void scalesRestoreManaAndCooldownSafely() {
        assertEquals(1, BloodMechanics.calculateRestoredBlood(0.1f, 1));
        assertEquals(0, BloodMechanics.calculateRestoredBlood(10, 0));
        assertEquals(50, BloodMechanics.scaleManaCost(25, 2));
        assertEquals(0, BloodMechanics.scaleManaCost(25, 0));
        assertEquals(10, BloodMechanics.scaleCooldown(20, 0.5));
        assertEquals(200, BloodMechanics.scaleCooldown(300, 2d / 3d));
        assertEquals(Integer.MAX_VALUE, BloodMechanics.scaleCooldown(Integer.MAX_VALUE, 10));
    }
}
