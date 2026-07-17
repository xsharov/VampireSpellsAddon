package com.vampirespells.addon.event;

import com.vampirespells.addon.VampireSpellsAddon;
import com.vampirespells.addon.config.AddonConfig;
import com.vampirespells.addon.integration.IronsSpellsBridge;
import com.vampirespells.addon.integration.VampirismBridge;
import com.vampirespells.addon.mechanics.BloodMechanics;
import com.vampirespells.addon.mechanics.SpellIds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class BloodSpellHandler {

    void onSpellPreCast(Object event) {
        Player caster = IronsSpellsBridge.player(event);
        ResourceLocation spellId = IronsSpellsBridge.spellId(event);
        if (!isResourceReplacementCandidate(event, caster, spellId)
                || IronsSpellsBridge.hasRecast(caster, spellId)) {
            return;
        }

        int manaCost = BloodCastHooks.adjustedManaCost(
                spellId,
                IronsSpellsBridge.spellManaCost(spellId, IronsSpellsBridge.spellLevel(event))
        );
        if (!usesBlood(caster, manaCost)) {
            return;
        }

        VampirismBridge.VampireContext vampire = VampirismBridge.vampire(caster);
        int bloodCost = calculateBloodCost(manaCost);
        if (BloodMechanics.canAffordBlood(vampire.bloodLevel(), bloodCost)) {
            return;
        }

        if (IronsSpellsBridge.cancelPreCast(event)) {
            IronsSpellsBridge.resetAdditionalCastData(caster);
            showInsufficientBlood(caster);
            VampireSpellsAddon.LOGGER.debug(
                    "Prevented Blood School cast {} by vampire {}: {} blood required",
                    spellId,
                    caster.getName().getString(),
                    bloodCost
            );
        }
    }

    void onSpellOnCast(Object event) {
        Player caster = IronsSpellsBridge.player(event);
        ResourceLocation spellId = IronsSpellsBridge.spellId(event);
        if (!isServerPlayer(caster) || spellId == null) {
            return;
        }

        boolean denied = handleSpellOnCast(event, caster, spellId);
        BloodCastHooks.recordCastOutcome(caster, spellId, denied);
    }

    void onSpellCooldown(Object event) {
        Player caster = IronsSpellsBridge.player(event);
        if (!isServerPlayer(caster)
                || !IronsSpellsBridge.isBloodSchool(IronsSpellsBridge.school(event))
                || !VampirismBridge.vampire(caster).isVampire()) {
            return;
        }

        int current = IronsSpellsBridge.cooldown(event);
        int adjusted = BloodMechanics.scaleCooldown(
                current,
                AddonConfig.VAMPIRE_BLOOD_SPELL_COOLDOWN_MULTIPLIER.get()
        );
        if (adjusted != current && IronsSpellsBridge.setCooldown(event, adjusted)) {
            VampireSpellsAddon.LOGGER.debug(
                    "Adjusted vampire Blood School cooldown for {} ({} -> {})",
                    caster.getName().getString(),
                    current,
                    adjusted
            );
        }
    }

    void onDeliveredSpellDamage(
            IronsSpellsBridge.SpellDamageInfo spellDamage,
            LivingEntity target,
            float actualHealthDamage
    ) {
        ResourceLocation spellId = spellDamage.spellId();
        if (!SpellIds.RAY_OF_SIPHONING.equals(spellId) && !SpellIds.DEVOUR.equals(spellId)) {
            return;
        }

        Player caster = spellDamage.caster();
        if (!isServerPlayer(caster) || actualHealthDamage <= 0f) {
            return;
        }

        VampirismBridge.VampireContext vampire = VampirismBridge.vampire(caster);
        if (!vampire.isVampire()) {
            return;
        }

        double multiplier = SpellIds.RAY_OF_SIPHONING.equals(spellId)
                ? AddonConfig.RAY_BLOOD_RESTORE_MULTIPLIER.get()
                : AddonConfig.DEVOUR_BLOOD_RESTORE_MULTIPLIER.get();
        double configuredSaturation = SpellIds.RAY_OF_SIPHONING.equals(spellId)
                ? AddonConfig.RAY_BLOOD_SATURATION.get()
                : AddonConfig.DEVOUR_BLOOD_SATURATION.get();
        float saturation = (float) Math.max(0d, configuredSaturation);

        int requested = BloodMechanics.calculateRestoredBlood(actualHealthDamage, multiplier);
        int restored = vampire.restoreBlood(requested, target, Math.max(0f, saturation));
        if (restored > 0) {
            VampireSpellsAddon.LOGGER.debug(
                    "{} restored {} blood to vampire {} from {} delivered health damage",
                    spellId,
                    restored,
                    caster.getName().getString(),
                    actualHealthDamage
            );
        }
    }

    private static boolean handleSpellOnCast(
            Object event,
            Player caster,
            ResourceLocation spellId
    ) {
        VampirismBridge.VampireContext vampire = VampirismBridge.vampire(caster);
        if (!vampire.isVampire()) {
            return false;
        }

        if (SpellIds.DEVOUR.equals(spellId)) {
            adjustDevourManaCost(event, caster);
        }

        if (!isResourceReplacementCandidate(event, caster, spellId)
                || IronsSpellsBridge.hasRecast(caster, spellId)) {
            return false;
        }

        int manaCost = Math.max(0, IronsSpellsBridge.manaCost(event));
        if (!usesBlood(caster, manaCost)) {
            return false;
        }

        int bloodCost = calculateBloodCost(manaCost);
        if (!IronsSpellsBridge.setManaCost(event, 0)) {
            VampireSpellsAddon.LOGGER.error(
                    "Blocked Blood School cast {} because its mana cost could not be replaced",
                    spellId
            );
            return true;
        }

        boolean paid = bloodCost == 0 || vampire.consumeBlood(bloodCost);
        if (!paid) {
            showInsufficientBlood(caster);
            VampireSpellsAddon.LOGGER.debug(
                    "Blocked completed Blood School cast {} by vampire {}: {} blood required",
                    spellId,
                    caster.getName().getString(),
                    bloodCost
            );
            return true;
        }

        VampireSpellsAddon.LOGGER.debug(
                "Replaced {} mana with {} blood for vampire Blood School cast {} by {}",
                manaCost,
                bloodCost,
                spellId,
                caster.getName().getString()
        );
        return false;
    }

    private static boolean isResourceReplacementCandidate(
            Object event,
            Player caster,
            ResourceLocation spellId
    ) {
        return isServerPlayer(caster)
                && !caster.isCreative()
                && spellId != null
                && !SpellIds.RAY_OF_SIPHONING.equals(spellId)
                && IronsSpellsBridge.isBloodSchool(IronsSpellsBridge.school(event))
                && IronsSpellsBridge.consumesMana(IronsSpellsBridge.castSource(event))
                && VampirismBridge.vampire(caster).isVampire();
    }

    private static boolean usesBlood(Player caster, int manaCost) {
        return BloodMechanics.shouldUseBlood(
                AddonConfig.ALWAYS_USE_BLOOD_FOR_VAMPIRE_BLOOD_SPELLS.get(),
                IronsSpellsBridge.currentMana(caster),
                manaCost
        );
    }

    private static void adjustDevourManaCost(Object event, Player caster) {
        int current = IronsSpellsBridge.manaCost(event);
        int adjusted = BloodCastHooks.adjustedManaCost(SpellIds.DEVOUR, current);
        if (adjusted != current && IronsSpellsBridge.setManaCost(event, adjusted)) {
            VampireSpellsAddon.LOGGER.debug(
                    "Adjusted Devour mana price for vampire {} ({} -> {})",
                    caster.getName().getString(),
                    current,
                    adjusted
            );
        }
    }

    private static int calculateBloodCost(int manaCost) {
        return BloodMechanics.calculateBloodCost(
                manaCost,
                AddonConfig.BLOOD_COST_MANA_FLOOR.get(),
                AddonConfig.BLOOD_COST_MANA_CEILING.get(),
                AddonConfig.BLOOD_COST_RATIO_MIN.get(),
                AddonConfig.BLOOD_COST_RATIO_MAX.get()
        );
    }

    private static void showInsufficientBlood(Player caster) {
        caster.displayClientMessage(
                Component.translatable("text.vampirism.container.not_enough_blood")
                        .withStyle(ChatFormatting.RED),
                true
        );
    }

    private static boolean isServerPlayer(Player player) {
        return player != null && !player.level().isClientSide();
    }
}
