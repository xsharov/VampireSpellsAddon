package com.vampirespells.addon.event;

import com.vampirespells.addon.VampireSpellsAddon;
import com.vampirespells.addon.integration.IronsSpellsBridge;
import com.vampirespells.addon.integration.VampirismBridge;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Owns loader-neutral integration state. Platform event adapters translate
 * Forge and NeoForge events into these callbacks.
 */
public final class SpellEventHandler {

    private static final CastStateTracker CAST_STATE = new CastStateTracker();
    private static final DamageStateTracker DAMAGE_STATE = new DamageStateTracker();
    private static final BloodSpellHandler BLOOD_SPELLS = new BloodSpellHandler();
    private static final HolySpellHandler HOLY_SPELLS = new HolySpellHandler(CAST_STATE);

    private static boolean registrationAttempted;
    private static volatile boolean registered;

    private SpellEventHandler() {
    }

    /** Resolves both parent APIs and installs all listeners once. */
    public static synchronized boolean register() {
        if (registrationAttempted) {
            return registered;
        }
        registrationAttempted = true;

        if (!IronsSpellsBridge.resolve() || !VampirismBridge.resolve()) {
            return false;
        }

        try {
            PlatformEvents.register();
            registered = true;
            return true;
        } catch (LinkageError | RuntimeException failure) {
            clearTransientState();
            VampireSpellsAddon.LOGGER.error(
                    "Could not register Vampire Spells Addon event integration; mechanics are disabled",
                    failure
            );
            return false;
        }
    }

    static boolean isRegistered() {
        return registered;
    }

    static void onSpellPreCast(Object event) {
        if (registered) {
            BLOOD_SPELLS.onSpellPreCast(event);
            HOLY_SPELLS.onSpellPreCast(event);
        }
    }

    static void onSpellOnCast(Object event) {
        if (registered) {
            BLOOD_SPELLS.onSpellOnCast(event);
        }
    }

    static void onSpellCooldown(Object event) {
        if (registered) {
            BLOOD_SPELLS.onSpellCooldown(event);
        }
    }

    static void onSpellDamage(Object event) {
        if (registered) {
            HOLY_SPELLS.onSpellDamage(event);
        }
    }

    static void onSpellHeal(Object event) {
        if (registered) {
            HOLY_SPELLS.onSpellHeal(event);
        }
    }

    static void captureSpellDamage(LivingEntity entity, DamageSource source) {
        if (registered && IronsSpellsBridge.inspectDamageSource(source).isPresent()) {
            DAMAGE_STATE.capture(entity, source);
        }
    }

    static void finishSpellDamage(LivingEntity entity, DamageSource source, float deliveredDamage) {
        if (!registered) {
            return;
        }
        dispatchDeliveredSpellDamage(entity, source, DAMAGE_STATE.finish(entity, source, deliveredDamage));
    }

    static void onFinalSpellDamage(LivingEntity entity, DamageSource source, float deliveredDamage) {
        if (registered) {
            float finiteDamage = Float.isFinite(deliveredDamage) ? Math.max(0f, deliveredDamage) : 0f;
            dispatchDeliveredSpellDamage(entity, source, Math.min(finiteDamage, Math.max(0f, entity.getHealth())));
        }
    }

    private static void dispatchDeliveredSpellDamage(
            LivingEntity entity,
            DamageSource source,
            float deliveredDamage
    ) {
        Optional<IronsSpellsBridge.SpellDamageInfo> spellDamage =
                IronsSpellsBridge.inspectDamageSource(source);
        if (spellDamage.isEmpty()) {
            return;
        }

        BLOOD_SPELLS.onDeliveredSpellDamage(spellDamage.get(), entity, deliveredDamage);
        HOLY_SPELLS.onDeliveredSpellDamage(spellDamage.get(), deliveredDamage);
    }

    static void onPlayerLoggedOut(Player player) {
        if (registered) {
            CAST_STATE.clearTransientPlayer(player);
        }
    }

    static void onPlayerClone(Player original, Player clone) {
        if (registered) {
            CAST_STATE.clearTransientPlayer(original);
            CAST_STATE.clearTransientPlayer(clone);
        }
    }

    static void onServerTick(int serverTick) {
        if (registered) {
            CAST_STATE.purgeExpired(serverTick);
        }
    }

    static void onServerStopped() {
        if (registered) {
            clearTransientState();
        }
    }

    static boolean shouldCancelLivingHeal(LivingEntity entity, float amount) {
        return registered && HOLY_SPELLS.shouldSuppressLivingHeal(entity, amount);
    }

    private static void clearTransientState() {
        CAST_STATE.clearTransientAll();
        DAMAGE_STATE.clear();
    }
}
