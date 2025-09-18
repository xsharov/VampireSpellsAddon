package com.vampirespells.addon.event;

import com.vampirespells.addon.VampireSpellsAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.lang.reflect.Method;
import java.util.Optional;

@EventBusSubscriber(modid = VampireSpellsAddon.MOD_ID)
public class SpellEventHandler {

    private static final ResourceLocation RAY_OF_SIPHONING_ID = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ray_of_siphoning");

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        try {

            // Use reflection to check if this is a SpellDamageSource
            Object source = event.getSource();
            Class<?> spellDamageSourceClass = Class.forName("io.redspace.ironsspellbooks.damage.SpellDamageSource");

            if (!spellDamageSourceClass.isInstance(source)) {
                return;
            }

            // Get spell from damage source using reflection
            Method spellMethod = spellDamageSourceClass.getMethod("spell");
            Object spell = spellMethod.invoke(source);

            // Get spell resource using reflection
            Method getSpellResourceMethod = spell.getClass().getMethod("getSpellResource");
            ResourceLocation spellResource = (ResourceLocation) getSpellResourceMethod.invoke(spell);

            // Check if this is Ray of Siphoning
            if (!spellResource.equals(RAY_OF_SIPHONING_ID)) {
                return;
            }

            // Get the attacker (caster) using reflection
            Method getEntityMethod_source = spellDamageSourceClass.getMethod("getEntity");
            Object attacker = getEntityMethod_source.invoke(source);

            if (!(attacker instanceof Player caster)) {
                return;
            }

            // Check if caster is a vampire using reflection
            Class<?> vampirismAPIClass = Class.forName("de.teamlapen.vampirism.api.VampirismAPI");
            Method vampirePlayerMethod = vampirismAPIClass.getMethod("vampirePlayer", Player.class);
            Object vampirePlayer = vampirePlayerMethod.invoke(null, caster);

            Method getLevelMethod = vampirePlayer.getClass().getMethod("getLevel");
            int vampireLevel = (Integer) getLevelMethod.invoke(vampirePlayer);

            // Check if caster is actually a vampire (has vampire level > 0)
            if (vampireLevel <= 0) {
                return; // Caster is not a vampire, let the spell work normally
            }

            // Caster is a vampire - cancel the damage and restore blood instead
            event.setNewDamage(0f);

            LivingEntity target = event.getEntity();

            // Get the damage amount that would have been dealt
            float damageAmount = event.getOriginalDamage();

            // Convert damage to blood restoration (1 damage = 1 blood point)
            int bloodAmount = Math.max(1, Math.round(damageAmount));

            // Create a blood context using reflection
            Class<?> drinkBloodContextClass = Class.forName("de.teamlapen.vampirism.api.entity.player.vampire.IDrinkBloodContext");
            Object context = java.lang.reflect.Proxy.newProxyInstance(
                drinkBloodContextClass.getClassLoader(),
                new Class[]{drinkBloodContextClass},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getEntity":
                            return Optional.of(target);
                        case "getStack":
                        case "getBlockState":
                        case "getBlockPos":
                            return Optional.empty();
                        default:
                            return null;
                    }
                }
            );

            // Restore blood to the vampire caster using reflection
            Method drinkBloodMethod = vampirePlayer.getClass().getMethod("drinkBlood", int.class, float.class, drinkBloodContextClass);
            drinkBloodMethod.invoke(vampirePlayer, bloodAmount, 0.5f, context);

            VampireSpellsAddon.LOGGER.info("Ray of Siphoning: Vampire {} gained {} blood from target {}",
                caster.getName().getString(), bloodAmount, target.getName().getString());

        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Could not process vampire blood restoration: {}", e.getMessage());
            // If we can't process vampire logic, let the original damage through
        }
    }
}