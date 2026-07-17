package com.vampirespells.addon.integration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolved public API surface shared by all bridge calls. */
record Contracts(
        Pre pre,
        OnCast onCast,
        Cooldown cooldown,
        Damage damage,
        Heal heal,
        Spell spell,
        Magic magic,
        Class<?> castSourceType,
        Method consumesMana
) {
    private static final String API = "io.redspace.ironsspellbooks.api.";
    private static final String EVENTS = API + "events.";
    private static final String SPELLS = API + "spells.";

    static Contracts resolve() throws ReflectiveOperationException {
        Class<?> schoolType = requireType(SPELLS + "SchoolType");
        Class<?> castSourceType = requireType(SPELLS + "CastSource");
        Class<?> abstractSpell = requireType(SPELLS + "AbstractSpell");
        Class<?> damageSource = requireType("io.redspace.ironsspellbooks.damage.SpellDamageSource");
        Class<?> magicData = requireType(API + "magic.MagicData");
        Class<?> playerRecasts = requireType(
                "io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts"
        );
        Class<?> registry = requireType(API + "registry.SpellRegistry");

        requireAssignable(DamageSource.class, damageSource);
        Method registryLookup = requireStaticMethod(
                registry, "getSpell", abstractSpell, ResourceLocation.class
        );
        Method magicForEntity = requireStaticMethod(
                magicData, "getPlayerMagicData", magicData, LivingEntity.class
        );

        return new Contracts(
                resolvePre(schoolType, castSourceType),
                resolveOnCast(schoolType, castSourceType),
                resolveCooldown(abstractSpell),
                resolveDamage(damageSource),
                resolveHeal(schoolType),
                new Spell(
                        abstractSpell,
                        schoolType,
                        damageSource,
                        requireMethod(abstractSpell, "getSpellResource", ResourceLocation.class),
                        requireMethod(abstractSpell, "getSchoolType", schoolType),
                        requireMethod(abstractSpell, "getManaCost", int.class, int.class),
                        requireMethod(schoolType, "getId", ResourceLocation.class),
                        requireMethod(damageSource, "spell", abstractSpell),
                        registryLookup
                ),
                new Magic(
                        magicForEntity,
                        requireMethod(magicData, "getMana", float.class),
                        requireMethod(magicData, "resetAdditionalCastData", void.class),
                        requireMethod(magicData, "getPlayerRecasts", playerRecasts),
                        requireMethod(playerRecasts, "hasRecastForSpell", boolean.class, String.class)
                ),
                castSourceType,
                requireMethod(castSourceType, "consumesMana", boolean.class)
        );
    }

    private static Pre resolvePre(Class<?> school, Class<?> source)
            throws ReflectiveOperationException {
        Class<?> type = requireType(EVENTS + "SpellPreCastEvent");
        requireAssignable(PlayerEvent.class, type);
        return new Pre(
                type,
                requireMethod(type, "getSpellId", String.class),
                requireMethod(type, "getSpellLevel", int.class),
                requireMethod(type, "getSchoolType", school),
                requireMethod(type, "getCastSource", source),
                requireMethod(type, "getEntity", Player.class),
                requireMethod(type, "setCanceled", void.class, boolean.class)
        );
    }

    private static OnCast resolveOnCast(Class<?> school, Class<?> source)
            throws ReflectiveOperationException {
        Class<?> type = requireType(EVENTS + "SpellOnCastEvent");
        requireAssignable(PlayerEvent.class, type);
        return new OnCast(
                type,
                requireMethod(type, "getSpellId", String.class),
                requireMethod(type, "getManaCost", int.class),
                requireMethod(type, "setManaCost", void.class, int.class),
                requireMethod(type, "getSchoolType", school),
                requireMethod(type, "getCastSource", source),
                requireMethod(type, "getEntity", Player.class)
        );
    }

    private static Cooldown resolveCooldown(Class<?> spell) throws ReflectiveOperationException {
        Class<?> type = requireType(EVENTS + "SpellCooldownAddedEvent$Pre");
        return new Cooldown(
                type,
                requireMethod(type, "getSpell", spell),
                requireMethod(type, "getEffectiveCooldown", int.class),
                requireMethod(type, "setEffectiveCooldown", void.class, int.class),
                requireMethod(type, "getEntity", Player.class)
        );
    }

    private static Damage resolveDamage(Class<?> source) throws ReflectiveOperationException {
        Class<?> type = requireType(EVENTS + "SpellDamageEvent");
        requireAssignable(LivingEvent.class, type);
        return new Damage(
                type,
                requireMethod(type, "getAmount", float.class),
                requireMethod(type, "setAmount", void.class, float.class),
                requireMethod(type, "getSpellDamageSource", source),
                requireMethod(type, "getEntity", LivingEntity.class)
        );
    }

    private static Heal resolveHeal(Class<?> school) throws ReflectiveOperationException {
        Class<?> type = requireType(EVENTS + "SpellHealEvent");
        requireAssignable(LivingEvent.class, type);
        return new Heal(
                type,
                requireMethod(type, "getHealAmount", float.class),
                requireMethod(type, "getSchoolType", school),
                requireMethod(type, "getEntity", LivingEntity.class),
                requireMethod(type, "getTargetEntity", LivingEntity.class)
        );
    }

    record Pre(
            Class<?> type, Method spellId, Method spellLevel, Method school,
            Method castSource, Method entity, Method setCanceled
    ) {
    }

    record OnCast(
            Class<?> type, Method spellId, Method manaCost, Method setManaCost,
            Method school, Method castSource, Method entity
    ) {
    }

    record Cooldown(
            Class<?> type, Method spell, Method cooldown, Method setCooldown, Method entity
    ) {
    }

    record Damage(
            Class<?> type, Method amount, Method setAmount, Method source, Method entity
    ) {
    }

    record Heal(Class<?> type, Method amount, Method school, Method entity, Method target) {
    }

    record Spell(
            Class<?> type, Class<?> schoolType, Class<?> damageSourceType,
            Method spellResource, Method school, Method manaCost, Method schoolId,
            Method damageSourceSpell, Method registryLookup
    ) {
    }

    record Magic(
            Method forEntity, Method mana, Method resetAdditionalCastData,
            Method playerRecasts, Method hasRecast
    ) {
    }

    private static Class<?> requireType(String name) throws ReflectiveOperationException {
        Class<?> type = Class.forName(name, false, IronsSpellsBridge.class.getClassLoader());
        if (!Modifier.isPublic(type.getModifiers())) {
            throw new ReflectiveOperationException(name + " is no longer public");
        }
        return type;
    }

    private static Method requireMethod(
            Class<?> owner, String name, Class<?> returnType, Class<?>... parameters
    ) throws ReflectiveOperationException {
        Method method = resolveMethod(owner, name, returnType, parameters);
        if (Modifier.isStatic(method.getModifiers())) {
            throw new ReflectiveOperationException(method.toGenericString() + " unexpectedly became static");
        }
        return method;
    }

    private static Method requireStaticMethod(
            Class<?> owner, String name, Class<?> returnType, Class<?>... parameters
    ) throws ReflectiveOperationException {
        Method method = resolveMethod(owner, name, returnType, parameters);
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new ReflectiveOperationException(method.toGenericString() + " is no longer static");
        }
        return method;
    }

    private static Method resolveMethod(
            Class<?> owner, String name, Class<?> returnType, Class<?>... parameters
    ) throws ReflectiveOperationException {
        Method method = owner.getMethod(name, parameters);
        Class<?> actual = method.getReturnType();
        boolean compatible = returnType.isPrimitive()
                ? actual == returnType
                : returnType.isAssignableFrom(actual);
        if (!compatible) {
            throw new ReflectiveOperationException(
                    method.toGenericString() + " has incompatible return type; expected "
                            + returnType.getTypeName()
            );
        }
        return method;
    }

    private static void requireAssignable(Class<?> parent, Class<?> child)
            throws ReflectiveOperationException {
        if (!parent.isAssignableFrom(child)) {
            throw new ReflectiveOperationException(
                    child.getTypeName() + " no longer extends " + parent.getTypeName()
            );
        }
    }
}
