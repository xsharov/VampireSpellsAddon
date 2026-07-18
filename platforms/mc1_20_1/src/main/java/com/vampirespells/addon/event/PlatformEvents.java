package com.vampirespells.addon.event;

import com.vampirespells.addon.integration.IronsSpellsBridge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

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

        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                LivingDamageEvent.class,
                event -> SpellEventHandler.onFinalSpellDamage(
                        event.getEntity(), event.getSource(), event.getAmount()
                )
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST,
                true,
                LivingHealEvent.class,
                PlatformEvents::onLivingHeal
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> SpellEventHandler.onPlayerLoggedOut(event.getEntity())
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                PlayerEvent.Clone.class,
                event -> SpellEventHandler.onPlayerClone(event.getOriginal(), event.getEntity())
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                TickEvent.ServerTickEvent.class,
                PlatformEvents::onServerTick
        );
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.NORMAL,
                false,
                ServerStoppedEvent.class,
                event -> SpellEventHandler.onServerStopped()
        );
    }

    private static void onLivingHeal(LivingHealEvent event) {
        if (SpellEventHandler.shouldCancelLivingHeal(event.getEntity(), event.getAmount())) {
            event.setCanceled(true);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SpellEventHandler.onServerTick(event.getServer().getTickCount());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerParentListener(
            EventPriority priority,
            Class<?> eventClass,
            Consumer<Object> handler
    ) {
        if (eventClass == null || !Event.class.isAssignableFrom(eventClass)) {
            throw new IllegalStateException("Resolved parent event is not a Forge Event: " + eventClass);
        }
        Consumer<Object> guardedHandler = event -> {
            if (SpellEventHandler.isRegistered()) {
                handler.accept(event);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(
                priority,
                false,
                (Class) eventClass,
                (Consumer) guardedHandler
        );
    }
}
