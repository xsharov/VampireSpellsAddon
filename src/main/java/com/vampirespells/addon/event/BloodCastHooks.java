package com.vampirespells.addon.event;

import com.vampirespells.addon.config.AddonConfig;
import com.vampirespells.addon.integration.IronsSpellsBridge;
import com.vampirespells.addon.integration.VampirismBridge;
import com.vampirespells.addon.mechanics.BloodMechanics;
import com.vampirespells.addon.mechanics.SpellIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Entry points used by the parent-class mixin without linking parent classes. */
public final class BloodCastHooks {

    private static final BloodCastOutcomeTracker OUTCOMES = new BloodCastOutcomeTracker();

    private BloodCastHooks() {
    }

    public static boolean shouldBypassManaRequirement(
            Object spell,
            int spellLevel,
            Player player
    ) {
        if (player == null || player.level().isClientSide() || player.isCreative()) {
            return false;
        }

        ResourceLocation spellId = IronsSpellsBridge.spellResource(spell);
        if (spellId == null
                || SpellIds.RAY_OF_SIPHONING.equals(spellId)
                || !IronsSpellsBridge.isBloodSpell(spell)
                || !VampirismBridge.vampire(player).isVampire()) {
            return false;
        }

        int manaCost = adjustedManaCost(
                spellId,
                IronsSpellsBridge.spellManaCost(spellId, spellLevel)
        );
        return BloodMechanics.shouldUseBlood(
                AddonConfig.ALWAYS_USE_BLOOD_FOR_VAMPIRE_BLOOD_SPELLS.get(),
                IronsSpellsBridge.currentMana(player),
                manaCost
        );
    }

    static int adjustedManaCost(ResourceLocation spellId, int manaCost) {
        return SpellIds.DEVOUR.equals(spellId)
                ? BloodMechanics.scaleManaCost(manaCost, AddonConfig.DEVOUR_MANA_MULTIPLIER.get())
                : Math.max(0, manaCost);
    }

    static void recordCastOutcome(Player player, ResourceLocation spellId, boolean denied) {
        OUTCOMES.record(player.getUUID(), spellId, denied);
    }

    public static boolean consumeDeniedCast(Player player, Object spell) {
        if (player == null) {
            return false;
        }
        ResourceLocation spellId = IronsSpellsBridge.spellResource(spell);
        return spellId != null && OUTCOMES.consumeDenied(player.getUUID(), spellId);
    }
}
