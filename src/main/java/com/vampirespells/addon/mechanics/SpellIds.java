package com.vampirespells.addon.mechanics;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class SpellIds {

    public static final ResourceLocation RAY_OF_SIPHONING = ironsSpell("ray_of_siphoning");
    public static final ResourceLocation DEVOUR = ironsSpell("devour");
    public static final ResourceLocation RAISE_DEAD = ironsSpell("raise_dead");
    public static final ResourceLocation HOLY_SCHOOL = ironsSpell("holy");

    public static final Set<ResourceLocation> BLOOD_COST_SPELLS = Set.of(
            ironsSpell("wither_skull"),
            ironsSpell("sacrifice"),
            RAISE_DEAD,
            ironsSpell("heartstop"),
            ironsSpell("blood_step"),
            ironsSpell("blood_slash"),
            ironsSpell("blood_needles"),
            ironsSpell("acupuncture")
    );

    public static final Set<ResourceLocation> HOLY_UTILITY_SPELLS = Set.of(
            ironsSpell("angel_wing"),
            ironsSpell("fortify"),
            ironsSpell("wisp"),
            ironsSpell("haste"),
            ironsSpell("cleanse"),
            ironsSpell("sunbeam")
    );

    private SpellIds() {
    }

    private static ResourceLocation ironsSpell(String path) {
        return ResourceLocation.fromNamespaceAndPath("irons_spellbooks", path);
    }
}
