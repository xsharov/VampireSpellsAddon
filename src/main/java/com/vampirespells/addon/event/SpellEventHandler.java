package com.vampirespells.addon.event;

import com.vampirespells.addon.VampireSpellsAddon;
import com.vampirespells.addon.integration.IronsSpellsBridge;
import com.vampirespells.addon.integration.VampirismBridge;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Registers the runtime-only parent-mod integration after both reflective
 * contracts have been validated.
 */
public final class SpellEventHandler {

    private static final CastStateTracker CAST_STATE = new CastStateTracker();
    private static final DamageStateTracker DAMAGE_STATE = new DamageStateTracker();
    private static final BloodSpellHandler BLOOD_SPELLS = new BloodSpellHandler(CAST_STATE);
    private static final HolySpellHandler HOLY_SPELLS = new HolySpellHandler(CAST_STATE);

    private static boolean registrationAttempted;
    private static volatile boolean registered;

    private SpellEventHandler() {
    }

    /**
     * Resolves both parent APIs and installs all listeners once. Integration
     * fails closed when either required contract is unavailable.
     */
    public static synchronized boolean register() {
        if (registrationAttempted) {
            return registered;
        }
        registrationAttempted = true;

        if (!IronsSpellsBridge.resolve() || !VampirismBridge.resolve()) {
            return false;
        }

        try {
            registerParentEvents();
            registerNeoForgeEvents();
            registered = true;
            return true;
        } catch (LinkageError | RuntimeException failure) {
            CAST_STATE.clearTransientAll();
            DAMAGE_STATE.clear();
            VampireSpellsAddon.LOGGER.error(
                    "Could not register Vampire Spells Addon event integration; mechanics are disabled",
                    failure
            );
            return false;
        }
    }

    private static void registerParentEvents() {
        registerParentListener(
                EventPriority.HIGHEST,
                IronsSpellsBridge.spellPreCastEventClass(),
                SpellEventHandler::onSpellPreCast
        );
        registerParentListener(
                EventPriority.LOWEST,
                IronsSpellsBridge.spellOnCastEventClass(),
                BLOOD_SPELLS::onSpellOnCast
        );
        registerParentListener(
                EventPriority.LOWEST,
                IronsSpellsBridge.spellCooldownPreEventClass(),
                BLOOD_SPELLS::onSpellCooldown
        );
        registerParentListener(
                EventPriority.LOWEST,
                IronsSpellsBridge.spellDamageEventClass(),
                HOLY_SPELLS::onSpellDamage
        );
        registerParentListener(
                EventPriority.LOWEST,
                IronsSpellsBridge.spellHealEventClass(),
                HOLY_SPELLS::onSpellHeal
        );
    }

    private static void registerNeoForgeEvents() {
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                LivingDamageEvent.Pre.class,
                SpellEventHandler::onLivingDamagePre
        );
        NeoForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                LivingDamageEvent.Post.class,
                SpellEventHandler::onLivingDamagePost
        );
        NeoForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST,
                true,
                LivingHealEvent.class,
                SpellEventHandler::onLivingHeal
        );
        NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                SpellEventHandler::onPlayerLoggedOut
        );
        NeoForge.EVENT_BUS.addListener(PlayerEvent.Clone.class, SpellEventHandler::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, SpellEventHandler::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, SpellEventHandler::onServerStopped);
    }

    private static void onSpellPreCast(Object event) {
        if (!registered) {
            return;
        }
        BLOOD_SPELLS.onSpellPreCast(event);
        HOLY_SPELLS.onSpellPreCast(event);
    }

    private static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!registered) {
            return;
        }
        if (IronsSpellsBridge.inspectDamageSource(event.getSource()).isPresent()) {
            DAMAGE_STATE.capture(event);
        }
    }

    private static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!registered) {
            return;
        }
        float actualHealthDamage = DAMAGE_STATE.finish(event);
        Optional<IronsSpellsBridge.SpellDamageInfo> spellDamage =
                IronsSpellsBridge.inspectDamageSource(event.getSource());
        if (spellDamage.isEmpty()) {
            return;
        }

        BLOOD_SPELLS.onDeliveredSpellDamage(spellDamage.get(), event.getEntity(), actualHealthDamage);
        HOLY_SPELLS.onDeliveredSpellDamage(spellDamage.get(), actualHealthDamage);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!registered) {
            return;
        }
        CAST_STATE.clearTransientPlayer(event.getEntity());
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (!registered) {
            return;
        }
        CAST_STATE.clearTransientPlayer(event.getOriginal());
        CAST_STATE.clearTransientPlayer(event.getEntity());
        if (event.isWasDeath()) {
            CAST_STATE.clearPersistentPlayer(event.getOriginal());
            CAST_STATE.clearPersistentPlayer(event.getEntity());
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!registered) {
            return;
        }
        CAST_STATE.purgeExpired(event.getServer().getTickCount());
        event.getServer().getPlayerList().getPlayers().forEach(CAST_STATE::tickPersistentDecisions);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        if (!registered) {
            return;
        }
        CAST_STATE.clearTransientAll();
        DAMAGE_STATE.clear();
    }

    private static void onLivingHeal(LivingHealEvent event) {
        if (registered) {
            HOLY_SPELLS.onLivingHeal(event);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerParentListener(
            EventPriority priority,
            Class<?> eventClass,
            Consumer<Object> handler
    ) {
        if (eventClass == null || !Event.class.isAssignableFrom(eventClass)) {
            throw new IllegalStateException("Resolved parent event is not a NeoForge Event: " + eventClass);
        }
        Consumer<Object> guardedHandler = event -> {
            if (registered) {
                handler.accept(event);
            }
        };
        NeoForge.EVENT_BUS.addListener(
                priority,
                false,
                (Class) eventClass,
                (Consumer) guardedHandler
        );
    }
}
