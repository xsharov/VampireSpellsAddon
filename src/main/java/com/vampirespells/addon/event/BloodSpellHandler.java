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

    private static final float MANA_EPSILON = 0.0001f;
    private static final int IMMEDIATE_COOLDOWN_LIFETIME_TICKS = 0;
    // RaiseDeadSpell currently gives recasts a hard-coded 10-minute lifetime.
    // Keep this in sync with upstream; the margin lets its timeout cooldown fire first.
    private static final int RAISE_DEAD_RECAST_LIFETIME_TICKS = 20 * 60 * 10 + 20;

    private final CastStateTracker state;

    BloodSpellHandler(CastStateTracker state) {
        this.state = state;
    }

    void onSpellPreCast(Object event) {
        Player caster = IronsSpellsBridge.player(event);
        ResourceLocation spellId = IronsSpellsBridge.spellId(event);
        if (!isServerPlayer(caster) || !SpellIds.DEVOUR.equals(spellId)) {
            return;
        }

        VampirismBridge.VampireContext vampire = VampirismBridge.vampire(caster);
        Object castSource = IronsSpellsBridge.castSource(event);
        if (!vampire.isVampire()
                || caster.isCreative()
                || !IronsSpellsBridge.consumesMana(castSource)) {
            return;
        }

        int baseManaCost = IronsSpellsBridge.spellManaCost(spellId, IronsSpellsBridge.spellLevel(event));
        int adjustedManaCost = BloodMechanics.scaleManaCost(
                baseManaCost,
                AddonConfig.DEVOUR_MANA_MULTIPLIER.get()
        );
        if (IronsSpellsBridge.currentMana(caster) + MANA_EPSILON >= adjustedManaCost) {
            return;
        }

        if (IronsSpellsBridge.cancelPreCast(event)) {
            IronsSpellsBridge.resetAdditionalCastData(caster);
            caster.displayClientMessage(
                    Component.translatable(
                            "ui.irons_spellbooks.cast_error_mana",
                            Component.translatable("spell.irons_spellbooks.devour")
                    ).withStyle(ChatFormatting.RED),
                    true
            );
            VampireSpellsAddon.LOGGER.debug(
                    "Prevented Devour cast by vampire {}: {} mana required",
                    caster.getName().getString(),
                    adjustedManaCost
            );
        }
    }

    void onSpellOnCast(Object event) {
        Player caster = IronsSpellsBridge.player(event);
        ResourceLocation spellId = IronsSpellsBridge.spellId(event);
        if (!isServerPlayer(caster) || spellId == null) {
            return;
        }

        VampirismBridge.VampireContext vampire = VampirismBridge.vampire(caster);
        if (SpellIds.DEVOUR.equals(spellId)) {
            if (vampire.isVampire()) {
                adjustDevourManaCost(event, caster);
            }
            return;
        }

        if (!SpellIds.BLOOD_COST_SPELLS.contains(spellId)) {
            return;
        }
        if (!vampire.isVampire()) {
            state.removeBloodDecision(caster, spellId);
            return;
        }

        int bloodCost = calculateBloodCost(IronsSpellsBridge.manaCost(event));
        boolean highBlood = BloodMechanics.isHighBlood(
                vampire.bloodLevel(),
                vampire.maxBlood(),
                AddonConfig.HIGH_BLOOD_THRESHOLD_FRACTION.get()
        );
        boolean spentBlood = highBlood && bloodCost > 0 && vampire.consumeBlood(bloodCost);
        boolean highOutcome = highBlood && (bloodCost == 0 || spentBlood);
        double cooldownMultiplier = highOutcome
                ? nonNegative(AddonConfig.HIGH_BLOOD_COOLDOWN_MULTIPLIER.get())
                : nonNegative(AddonConfig.LOW_BLOOD_COOLDOWN_MULTIPLIER.get());

        CastStateTracker.BloodCastDecision decision = new CastStateTracker.BloodCastDecision(
                spentBlood,
                bloodCost,
                cooldownMultiplier
        );
        state.storeBloodDecision(
                caster,
                spellId,
                decision,
                serverTick(caster),
                decisionLifetime(spellId)
        );

        VampireSpellsAddon.LOGGER.debug(
                "Blood cast {} by {}: highBlood={}, spentBlood={}, cost={}, cooldownMultiplier={}",
                spellId,
                caster.getName().getString(),
                highBlood,
                spentBlood,
                bloodCost,
                cooldownMultiplier
        );
    }

    void onSpellCooldown(Object event) {
        Player caster = IronsSpellsBridge.player(event);
        ResourceLocation spellId = IronsSpellsBridge.spellId(event);
        if (!isServerPlayer(caster) || spellId == null) {
            return;
        }

        CastStateTracker.BloodCastDecision decision = state.removeBloodDecision(caster, spellId);
        if (decision == null) {
            return;
        }

        int current = IronsSpellsBridge.cooldown(event);
        int adjusted = BloodMechanics.scaleCooldown(current, decision.cooldownMultiplier());
        if (IronsSpellsBridge.setCooldown(event, adjusted)) {
            VampireSpellsAddon.LOGGER.debug(
                    "Adjusted cooldown for {} by {} ({} -> {}, spentBlood={})",
                    spellId,
                    caster.getName().getString(),
                    current,
                    adjusted,
                    decision.spentBlood()
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

    private static void adjustDevourManaCost(Object event, Player caster) {
        int current = IronsSpellsBridge.manaCost(event);
        int adjusted = BloodMechanics.scaleManaCost(current, AddonConfig.DEVOUR_MANA_MULTIPLIER.get());
        if (adjusted != current && IronsSpellsBridge.setManaCost(event, adjusted)) {
            VampireSpellsAddon.LOGGER.debug(
                    "Adjusted Devour mana cost for vampire {} ({} -> {})",
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

    private static boolean isServerPlayer(Player player) {
        return player != null && !player.level().isClientSide();
    }

    private static int serverTick(Player player) {
        return player.getServer() == null ? player.tickCount : player.getServer().getTickCount();
    }

    private static int decisionLifetime(ResourceLocation spellId) {
        return SpellIds.RAISE_DEAD.equals(spellId)
                ? RAISE_DEAD_RECAST_LIFETIME_TICKS
                : IMMEDIATE_COOLDOWN_LIFETIME_TICKS;
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0d, value) : 0d;
    }
}
