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
    void comparesFractionalThresholdWithoutIntegerRounding() {
        assertFalse(BloodMechanics.isHighBlood(6, 20, 0.31));
        assertTrue(BloodMechanics.isHighBlood(7, 20, 0.31));
        assertTrue(BloodMechanics.isHighBlood(0, 20, 0));
        assertFalse(BloodMechanics.isHighBlood(20, 0, 0));
    }

    @Test
    void scalesRestoreManaAndCooldownSafely() {
        assertEquals(1, BloodMechanics.calculateRestoredBlood(0.1f, 1));
        assertEquals(0, BloodMechanics.calculateRestoredBlood(10, 0));
        assertEquals(50, BloodMechanics.scaleManaCost(25, 2));
        assertEquals(0, BloodMechanics.scaleManaCost(25, 0));
        assertEquals(10, BloodMechanics.scaleCooldown(20, 0.5));
        assertEquals(Integer.MAX_VALUE, BloodMechanics.scaleCooldown(Integer.MAX_VALUE, 10));
    }
}
