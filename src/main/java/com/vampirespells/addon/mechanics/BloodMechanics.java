package com.vampirespells.addon.mechanics;

public final class BloodMechanics {

    private static final float MANA_EPSILON = 0.0001f;

    private BloodMechanics() {
    }

    public static int calculateBloodCost(
            int manaCost,
            double floorMana,
            double ceilingMana,
            double minimumRatio,
            double maximumRatio
    ) {
        if (manaCost <= 0) {
            return 0;
        }

        double floor = nonNegative(floorMana);
        double ceiling = Math.max(floor, nonNegative(ceilingMana));
        double minimum = nonNegative(minimumRatio);
        double maximum = nonNegative(maximumRatio);

        double ratio;
        if (ceiling <= floor) {
            ratio = manaCost >= ceiling ? maximum : minimum;
        } else {
            double normalized = clamp((manaCost - floor) / (ceiling - floor), 0d, 1d);
            ratio = minimum + (maximum - minimum) * normalized;
        }

        return ceilToInt(manaCost * ratio);
    }

    public static boolean shouldUseBlood(boolean alwaysUseBlood, float currentMana, int manaCost) {
        if (alwaysUseBlood) {
            return true;
        }
        if (manaCost <= 0) {
            return false;
        }
        return !Float.isFinite(currentMana) || currentMana + MANA_EPSILON < manaCost;
    }

    public static boolean canAffordBlood(int currentBlood, int bloodCost) {
        return currentBlood >= 0 && bloodCost >= 0 && currentBlood >= bloodCost;
    }

    public static int calculateRestoredBlood(float actualDamage, double multiplier) {
        if (!Float.isFinite(actualDamage) || actualDamage <= 0f) {
            return 0;
        }
        double scaled = actualDamage * nonNegative(multiplier);
        if (!Double.isFinite(scaled) || scaled <= 0d) {
            return scaled > 0d ? Integer.MAX_VALUE : 0;
        }
        return Math.max(1, roundToInt(scaled));
    }

    public static int scaleManaCost(int manaCost, double multiplier) {
        if (manaCost <= 0) {
            return 0;
        }
        double safeMultiplier = nonNegative(multiplier);
        if (safeMultiplier <= 0d) {
            return 0;
        }
        return Math.max(1, roundToInt(manaCost * safeMultiplier));
    }

    public static int scaleCooldown(int cooldownTicks, double multiplier) {
        if (cooldownTicks <= 0) {
            return 0;
        }
        double safeMultiplier = nonNegative(multiplier);
        if (safeMultiplier <= 0d) {
            return 0;
        }
        return Math.max(1, roundToInt(cooldownTicks * safeMultiplier));
    }

    private static int ceilToInt(double value) {
        if (!Double.isFinite(value)) {
            return value > 0d ? Integer.MAX_VALUE : 0;
        }
        if (value <= 0d) {
            return 0;
        }
        return (int) Math.min(Math.ceil(value), Integer.MAX_VALUE);
    }

    private static int roundToInt(double value) {
        if (!Double.isFinite(value)) {
            return value > 0d ? Integer.MAX_VALUE : 0;
        }
        if (value <= 0d) {
            return 0;
        }
        return (int) Math.min(Math.round(value), Integer.MAX_VALUE);
    }

    private static double nonNegative(double value) {
        return Math.max(0d, finiteOrZero(value));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0d;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
