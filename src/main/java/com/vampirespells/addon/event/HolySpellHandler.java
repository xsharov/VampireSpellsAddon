package com.vampirespells.addon.event;

import com.vampirespells.addon.VampireSpellsAddon;
import com.vampirespells.addon.integration.IronsSpellsBridge;
import com.vampirespells.addon.integration.VampirismBridge;
import com.vampirespells.addon.mechanics.SpellIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class HolySpellHandler {

    private static final float HOLY_UTILITY_SELF_DAMAGE = 5f;

    private final CastStateTracker state;

    HolySpellHandler(CastStateTracker state) {
        this.state = state;
    }

    void onSpellPreCast(Object event) {
        Player caster = IronsSpellsBridge.player(event);
        ResourceLocation spellId = IronsSpellsBridge.spellId(event);
        if (!isServerEntity(caster)
                || spellId == null
                || !SpellIds.HOLY_UTILITY_SPELLS.contains(spellId)
                || !IronsSpellsBridge.isHolySchool(IronsSpellsBridge.school(event))
                || !VampirismBridge.vampire(caster).isVampire()) {
            return;
        }

        if (IronsSpellsBridge.cancelPreCast(event)) {
            IronsSpellsBridge.resetAdditionalCastData(caster);
            damageEntity(caster, HOLY_UTILITY_SELF_DAMAGE, "holy utility penalty");
            VampireSpellsAddon.LOGGER.debug(
                    "Prevented holy utility spell {} for vampire {}",
                    spellId,
                    caster.getName().getString()
            );
        }
    }

    void onSpellDamage(Object event) {
        LivingEntity target = IronsSpellsBridge.damageTarget(event);
        if (!isServerEntity(target)
                || target instanceof Player
                || !VampirismBridge.isVampireEntity(target)) {
            return;
        }

        IronsSpellsBridge.inspectDamageSource(IronsSpellsBridge.damageSource(event))
                .filter(IronsSpellsBridge.SpellDamageInfo::holy)
                .ifPresent(ignored -> doubleNpcHolyDamage(event, target));
    }

    void onDeliveredSpellDamage(
            IronsSpellsBridge.SpellDamageInfo spellDamage,
            float actualHealthDamage
    ) {
        Player caster = spellDamage.caster();
        if (!spellDamage.holy()
                || !isServerEntity(caster)
                || actualHealthDamage <= 0f
                || !VampirismBridge.vampire(caster).isVampire()) {
            return;
        }
        damageEntity(caster, actualHealthDamage, "holy damage reflection");
    }

    void onSpellHeal(Object event) {
        if (!IronsSpellsBridge.isHolySchool(IronsSpellsBridge.school(event))) {
            return;
        }

        float healAmount = IronsSpellsBridge.heal(event);
        LivingEntity caster = IronsSpellsBridge.healCaster(event);
        LivingEntity target = IronsSpellsBridge.healTarget(event);
        if (!Float.isFinite(healAmount) || healAmount <= 0f || !isServerEntity(target)) {
            return;
        }

        boolean sameEntity = caster == target;
        if (!sameEntity
                && caster instanceof Player casterPlayer
                && VampirismBridge.vampire(casterPlayer).isVampire()) {
            damageEntity(casterPlayer, healAmount, "holy heal caster penalty");
        }

        if (target instanceof Player targetPlayer
                && VampirismBridge.vampire(targetPlayer).isVampire()) {
            damageEntity(targetPlayer, healAmount, "holy heal target penalty");
            state.queueHolyHeal(targetPlayer, healAmount, serverTick(targetPlayer));
        }
    }

    boolean shouldSuppressLivingHeal(LivingEntity target, float amount) {
        if (!isServerEntity(target)) {
            return false;
        }

        int serverTick = target.getServer() == null ? target.tickCount : target.getServer().getTickCount();
        return state.consumeMatchingHolyHeal(target, amount, serverTick);
    }

    private static void doubleNpcHolyDamage(Object event, LivingEntity target) {
        float original = IronsSpellsBridge.damage(event);
        if (!Float.isFinite(original) || original <= 0f) {
            return;
        }

        float doubled = (float) Math.min((double) original * 2d, Float.MAX_VALUE);
        if (IronsSpellsBridge.setDamage(event, doubled)) {
            VampireSpellsAddon.LOGGER.debug(
                    "Doubled holy damage to Vampirism NPC {} ({} -> {})",
                    target.getName().getString(),
                    original,
                    doubled
            );
        }
    }

    private static void damageEntity(LivingEntity entity, float amount, String reason) {
        if (!isServerEntity(entity) || !Float.isFinite(amount) || amount <= 0f) {
            return;
        }
        entity.hurt(entity.damageSources().magic(), amount);
        VampireSpellsAddon.LOGGER.debug(
                "Applied {} damage as {} to {}",
                amount,
                reason,
                entity.getName().getString()
        );
    }

    private static boolean isServerEntity(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide();
    }

    private static int serverTick(Player player) {
        return player.getServer() == null ? player.tickCount : player.getServer().getTickCount();
    }
}
