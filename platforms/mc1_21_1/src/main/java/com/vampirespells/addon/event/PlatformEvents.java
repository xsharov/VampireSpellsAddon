package com.vampirespells.addon.event;

import com.vampirespells.addon.integration.IronsSpellsBridge;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.function.Consumer;

final class PlatformEvents {

    private PlatformEvents() {
    }

    static void register() {
        registerParentListener(
                EventPriority.HIGHEST,
                IronsSpellsBridge.spellPreCastEventClass(),
                SpellEventHandler::onSpellPreCast
        );
        registerParentListener(
                EventPriority.LOWEST,
                IronsSpellsBridge.spellOnCastEventClass(),
                SpellEventHandler::onSpellOnCast
        );
        registerParentListener(
                EventPriority.LOWEST,
                IronsSpellsBridge.spellCooldownPreEventClass(),
                SpellEventHandler::onSpellCooldown
        );
        registerParentListener(
                EventPriority.LOWEST,
                IronsSpellsBridge.spellDamageEventClass(),
                SpellEventHandler::onSpellDamage
        );
        registerParentListener(
                EventPriority.LOWEST,
                IronsSpellsBridge.spellHealEventClass(),
                SpellEventHandler::onSpellHeal
        );

        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                LivingDamageEvent.Pre.class,
                event -> SpellEventHandler.captureSpellDamage(event.getEntity(), event.getSource())
        );
        NeoForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                LivingDamageEvent.Post.class,
                event -> SpellEventHandler.finishSpellDamage(
                        event.getEntity(), event.getSource(), event.getNewDamage()
                )
        );
        NeoForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST,
                true,
                LivingHealEvent.class,
                PlatformEvents::onLivingHeal
        );
        NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> SpellEventHandler.onPlayerLoggedOut(event.getEntity())
        );
        NeoForge.EVENT_BUS.addListener(
                PlayerEvent.Clone.class,
                event -> SpellEventHandler.onPlayerClone(event.getOriginal(), event.getEntity())
        );
        NeoForge.EVENT_BUS.addListener(
                ServerTickEvent.Post.class,
                event -> SpellEventHandler.onServerTick(event.getServer().getTickCount())
        );
        NeoForge.EVENT_BUS.addListener(
                ServerStoppedEvent.class,
                event -> SpellEventHandler.onServerStopped()
        );
    }

    private static void onLivingHeal(LivingHealEvent event) {
        if (SpellEventHandler.shouldCancelLivingHeal(event.getEntity(), event.getAmount())) {
            event.setCanceled(true);
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
            if (SpellEventHandler.isRegistered()) {
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
