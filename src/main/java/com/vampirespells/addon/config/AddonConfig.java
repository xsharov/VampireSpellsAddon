package com.vampirespells.addon.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AddonConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue BLOOD_COST_RATIO_MIN;
    public static final ModConfigSpec.DoubleValue BLOOD_COST_RATIO_MAX;
    public static final ModConfigSpec.IntValue BLOOD_COST_MANA_FLOOR;
    public static final ModConfigSpec.IntValue BLOOD_COST_MANA_CEILING;

    public static final ModConfigSpec.DoubleValue HIGH_BLOOD_THRESHOLD_FRACTION;
    public static final ModConfigSpec.DoubleValue HIGH_BLOOD_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LOW_BLOOD_COOLDOWN_MULTIPLIER;

    public static final ModConfigSpec.DoubleValue DEVOUR_MANA_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DEVOUR_BLOOD_RESTORE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DEVOUR_BLOOD_SATURATION;

    public static final ModConfigSpec.DoubleValue RAY_BLOOD_RESTORE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RAY_BLOOD_SATURATION;

    private AddonConfig() {
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Blood spell integration settings").push("blood_spells");

        BLOOD_COST_MANA_FLOOR = builder
                .comment("Minimum mana cost used when mapping mana to blood cost ratios.")
                .defineInRange("bloodCostManaFloor", 20, 0, Integer.MAX_VALUE);
        BLOOD_COST_MANA_CEILING = builder
                .comment("Maximum mana cost used when mapping mana to blood cost ratios.")
                .defineInRange("bloodCostManaCeiling", 140, 1, Integer.MAX_VALUE);

        BLOOD_COST_RATIO_MIN = builder
                .comment("Blood cost ratio applied to spells at or below the mana floor (blood per mana).")
                .defineInRange("bloodCostRatioMin", 0.05d, 0d, 5d);
        BLOOD_COST_RATIO_MAX = builder
                .comment("Blood cost ratio applied to spells at or above the mana ceiling (blood per mana).")
                .defineInRange("bloodCostRatioMax", 0.05d, 0d, 5d);

        HIGH_BLOOD_THRESHOLD_FRACTION = builder
                .comment("Fraction of maximum blood required to enter the high-blood casting state.")
                .defineInRange("highBloodThresholdFraction", 0.5d, 0d, 1d);
        HIGH_BLOOD_COOLDOWN_MULTIPLIER = builder
                .comment("Cooldown multiplier applied when the caster has at least the configured high-blood threshold.")
                .defineInRange("highBloodCooldownMultiplier", 0.5d, 0d, 10d);
        LOW_BLOOD_COOLDOWN_MULTIPLIER = builder
                .comment("Cooldown multiplier applied when the caster is below the high-blood threshold and casts without spending blood.")
                .defineInRange("lowBloodCooldownMultiplier", 2d, 0d, 10d);

        DEVOUR_MANA_MULTIPLIER = builder
                .comment("Mana cost multiplier applied to the Devour spell.")
                .defineInRange("devourManaMultiplier", 2d, 0d, 10d);
        DEVOUR_BLOOD_RESTORE_MULTIPLIER = builder
                .comment("Blood restoration multiplier applied to the Devour spell (relative to damage dealt).")
                .defineInRange("devourBloodRestoreMultiplier", 2d, 0d, 10d);
        DEVOUR_BLOOD_SATURATION = builder
                .comment("Saturation modifier used when restoring blood with the Devour spell.")
                .defineInRange("devourBloodSaturation", 0.6d, 0d, 5d);

        RAY_BLOOD_RESTORE_MULTIPLIER = builder
                .comment("Blood restoration multiplier applied to the Ray of Siphoning spell (relative to damage dealt).")
                .defineInRange("rayBloodRestoreMultiplier", 1d, 0d, 10d);
        RAY_BLOOD_SATURATION = builder
                .comment("Saturation modifier used when restoring blood with Ray of Siphoning.")
                .defineInRange("rayBloodSaturation", 0.5d, 0d, 5d);

        builder.pop();

        SPEC = builder.build();
    }
}
