package com.vampirespells.addon.integration;

import com.vampirespells.addon.VampireSpellsAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reflection-only access to the public Iron's Spells API used by this addon.
 *
 * <p>Every class and method is resolved once as one all-or-nothing contract.
 * A resolution or invocation failure permanently disables this bridge and is
 * reported once with its full cause.</p>
 */
public final class IronsSpellsBridge {

    private static final ResourceLocation HOLY_SCHOOL =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "holy");
    private static final ResourceLocation BLOOD_SCHOOL =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "blood");
    private static final Object FAILED_INVOCATION = new Object();
    private static final Object RESOLUTION_LOCK = new Object();
    private static final AtomicBoolean DIAGNOSTIC_REPORTED = new AtomicBoolean();

    private static volatile ResolutionState state = ResolutionState.UNRESOLVED;
    private static volatile Contracts contracts;

    private IronsSpellsBridge() {
    }

    /** Resolves all supported floor/current API contracts on the first call. */
    public static boolean resolve() {
        ResolutionState current = state;
        if (current != ResolutionState.UNRESOLVED) {
            return current == ResolutionState.AVAILABLE;
        }

        synchronized (RESOLUTION_LOCK) {
            if (state == ResolutionState.UNRESOLVED) {
                try {
                    contracts = Contracts.resolve();
                    state = ResolutionState.AVAILABLE;
                } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                    disable("resolving public Iron's Spells API contracts", failure);
                }
            }
            return state == ResolutionState.AVAILABLE;
        }
    }

    public static boolean available() {
        return resolve();
    }

    public static Class<?> spellPreCastEventClass() {
        Contracts resolved = activeContracts();
        return resolved == null ? null : resolved.pre().type();
    }

    public static Class<?> spellOnCastEventClass() {
        Contracts resolved = activeContracts();
        return resolved == null ? null : resolved.onCast().type();
    }

    public static Class<?> spellCooldownPreEventClass() {
        Contracts resolved = activeContracts();
        return resolved == null ? null : resolved.cooldown().type();
    }

    public static Class<?> spellDamageEventClass() {
        Contracts resolved = activeContracts();
        return resolved == null ? null : resolved.damage().type();
    }

    public static Class<?> spellHealEventClass() {
        Contracts resolved = activeContracts();
        return resolved == null ? null : resolved.heal().type();
    }

    /** Returns the parsed spell id for pre-cast, on-cast, or cooldown events. */
    public static ResourceLocation spellId(Object event) {
        Contracts resolved = activeContracts();
        if (resolved == null) {
            return null;
        }

        Method method;
        if (resolved.pre().type().isInstance(event)) {
            method = resolved.pre().spellId();
        } else if (resolved.onCast().type().isInstance(event)) {
            method = resolved.onCast().spellId();
        } else if (resolved.cooldown().type().isInstance(event)) {
            Object spell = requiredValue(resolved, resolved.cooldown().spell(), event);
            return spell == null ? null : spellId(resolved, spell);
        } else {
            return null;
        }

        String id = typedValue(resolved, method, event, String.class);
        if (id == null) {
            return null;
        }
        try {
            return ResourceLocation.parse(id);
        } catch (RuntimeException failure) {
            disable("parsing an Iron's Spells spell id", failure);
            return null;
        }
    }

    public static int spellLevel(Object event) {
        Contracts resolved = activeContracts();
        if (resolved == null) {
            return 0;
        }
        if (resolved.pre().type().isInstance(event)) {
            return intValue(resolved, resolved.pre().spellLevel(), event);
        }
        return 0;
    }

    /** Returns an opaque SchoolType handle. */
    public static Object school(Object event) {
        Contracts resolved = activeContracts();
        if (resolved == null) {
            return null;
        }
        if (resolved.pre().type().isInstance(event)) {
            return requiredValue(resolved, resolved.pre().school(), event);
        }
        if (resolved.onCast().type().isInstance(event)) {
            return requiredValue(resolved, resolved.onCast().school(), event);
        }
        if (resolved.cooldown().type().isInstance(event)) {
            Object spell = requiredValue(resolved, resolved.cooldown().spell(), event);
            return spell == null ? null : requiredValue(resolved, resolved.spell().school(), spell);
        }
        if (resolved.heal().type().isInstance(event)) {
            return requiredValue(resolved, resolved.heal().school(), event);
        }
        return null;
    }

    /** Returns an opaque CastSource handle. */
    public static Object castSource(Object event) {
        Contracts resolved = activeContracts();
        if (resolved == null) {
            return null;
        }
        if (resolved.pre().type().isInstance(event)) {
            return requiredValue(resolved, resolved.pre().castSource(), event);
        }
        if (resolved.onCast().type().isInstance(event)) {
            return requiredValue(resolved, resolved.onCast().castSource(), event);
        }
        return null;
    }

    public static boolean consumesMana(Object castSource) {
        Contracts resolved = activeContracts();
        return resolved != null
                && resolved.castSourceType().isInstance(castSource)
                && booleanValue(resolved, resolved.consumesMana(), castSource);
    }

    public static int manaCost(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.onCast().type().isInstance(event)
                ? intValue(resolved, resolved.onCast().manaCost(), event)
                : 0;
    }

    public static boolean setManaCost(Object event, int manaCost) {
        Contracts resolved = activeContracts();
        return resolved != null
                && resolved.onCast().type().isInstance(event)
                && invokeVoid(resolved, resolved.onCast().setManaCost(), event, manaCost);
    }

    public static int cooldown(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.cooldown().type().isInstance(event)
                ? intValue(resolved, resolved.cooldown().cooldown(), event)
                : 0;
    }

    public static boolean setCooldown(Object event, int cooldown) {
        Contracts resolved = activeContracts();
        return resolved != null
                && resolved.cooldown().type().isInstance(event)
                && invokeVoid(resolved, resolved.cooldown().setCooldown(), event, cooldown);
    }

    public static float damage(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.damage().type().isInstance(event)
                ? floatValue(resolved, resolved.damage().amount(), event)
                : 0f;
    }

    public static boolean setDamage(Object event, float amount) {
        Contracts resolved = activeContracts();
        return resolved != null
                && resolved.damage().type().isInstance(event)
                && invokeVoid(resolved, resolved.damage().setAmount(), event, amount);
    }

    public static DamageSource damageSource(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.damage().type().isInstance(event)
                ? typedValue(resolved, resolved.damage().source(), event, DamageSource.class)
                : null;
    }

    public static LivingEntity damageTarget(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.damage().type().isInstance(event)
                ? typedValue(resolved, resolved.damage().entity(), event, LivingEntity.class)
                : null;
    }

    /** SpellHealEvent exposes an immutable amount; suppression happens later in LivingHealEvent. */
    public static float heal(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.heal().type().isInstance(event)
                ? floatValue(resolved, resolved.heal().amount(), event)
                : 0f;
    }

    public static LivingEntity healCaster(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.heal().type().isInstance(event)
                ? typedValue(resolved, resolved.heal().entity(), event, LivingEntity.class)
                : null;
    }

    public static LivingEntity healTarget(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.heal().type().isInstance(event)
                ? typedValue(resolved, resolved.heal().target(), event, LivingEntity.class)
                : null;
    }

    public static Player player(Object event) {
        Contracts resolved = activeContracts();
        if (resolved == null) {
            return null;
        }
        if (resolved.pre().type().isInstance(event)) {
            return typedValue(resolved, resolved.pre().entity(), event, Player.class);
        }
        if (resolved.onCast().type().isInstance(event)) {
            return typedValue(resolved, resolved.onCast().entity(), event, Player.class);
        }
        if (resolved.cooldown().type().isInstance(event)) {
            return typedValue(resolved, resolved.cooldown().entity(), event, Player.class);
        }
        return null;
    }

    public static boolean cancelPreCast(Object event) {
        Contracts resolved = activeContracts();
        return resolved != null
                && resolved.pre().type().isInstance(event)
                && invokeVoid(resolved, resolved.pre().setCanceled(), event, true);
    }

    public static boolean isHolySchool(Object school) {
        Contracts resolved = activeContracts();
        return resolved != null && isHolySchool(resolved, school);
    }

    public static boolean isBloodSchool(Object school) {
        Contracts resolved = activeContracts();
        return resolved != null && hasSchoolId(resolved, school, BLOOD_SCHOOL);
    }

    /** Returns whether an opaque AbstractSpell handle belongs to the Blood School. */
    public static boolean isBloodSpell(Object spell) {
        Contracts resolved = activeContracts();
        if (resolved == null || !resolved.spell().type().isInstance(spell)) {
            return false;
        }
        Object school = requiredValue(resolved, resolved.spell().school(), spell);
        return school != null && hasSchoolId(resolved, school, BLOOD_SCHOOL);
    }

    /** Returns the resource id of an opaque AbstractSpell handle. */
    public static ResourceLocation spellResource(Object spell) {
        Contracts resolved = activeContracts();
        return resolved != null && resolved.spell().type().isInstance(spell)
                ? spellId(resolved, spell)
                : null;
    }

    /** Looks up the configured spell mana cost through SpellRegistry. */
    public static int spellManaCost(ResourceLocation spellId, int spellLevel) {
        Contracts resolved = activeContracts();
        if (resolved == null || spellId == null) {
            return 0;
        }
        Object spell = requiredValue(resolved, resolved.spell().registryLookup(), null, spellId);
        return spell == null ? 0 : intValue(resolved, resolved.spell().manaCost(), spell, spellLevel);
    }

    public static float currentMana(LivingEntity entity) {
        Contracts resolved = activeContracts();
        if (resolved == null || entity == null) {
            return 0f;
        }
        Object magicData = requiredValue(resolved, resolved.magic().forEntity(), null, entity);
        return magicData == null ? 0f : floatValue(resolved, resolved.magic().mana(), magicData);
    }

    public static boolean hasRecast(LivingEntity entity, ResourceLocation spellId) {
        Contracts resolved = activeContracts();
        if (resolved == null || entity == null || spellId == null) {
            return false;
        }
        Object magicData = requiredValue(resolved, resolved.magic().forEntity(), null, entity);
        if (magicData == null) {
            return false;
        }
        Object recasts = requiredValue(resolved, resolved.magic().playerRecasts(), magicData);
        return recasts != null && booleanValue(
                resolved,
                resolved.magic().hasRecast(),
                recasts,
                spellId.toString()
        );
    }

    public static boolean resetAdditionalCastData(LivingEntity entity) {
        Contracts resolved = activeContracts();
        if (resolved == null || entity == null) {
            return false;
        }
        Object magicData = requiredValue(resolved, resolved.magic().forEntity(), null, entity);
        return magicData != null
                && invokeVoid(resolved, resolved.magic().resetAdditionalCastData(), magicData);
    }

    /** Returns empty for ordinary damage sources or when the integration is unavailable. */
    public static Optional<SpellDamageInfo> inspectDamageSource(DamageSource source) {
        Contracts resolved = activeContracts();
        if (resolved == null || source == null || !resolved.spell().damageSourceType().isInstance(source)) {
            return Optional.empty();
        }

        Object spell = requiredValue(resolved, resolved.spell().damageSourceSpell(), source);
        if (spell == null) {
            return Optional.empty();
        }
        ResourceLocation spellId = spellId(resolved, spell);
        Object school = requiredValue(resolved, resolved.spell().school(), spell);
        if (spellId == null || school == null) {
            return Optional.empty();
        }

        try {
            Player caster = source.getEntity() instanceof Player player ? player : null;
            return active(resolved)
                    ? Optional.of(new SpellDamageInfo(spellId, caster, isHolySchool(resolved, school)))
                    : Optional.empty();
        } catch (LinkageError | RuntimeException failure) {
            disable("inspecting an Iron's Spells damage source", failure);
            return Optional.empty();
        }
    }

    /** The caster is null when the spell damage was not caused by a player. */
    public record SpellDamageInfo(ResourceLocation spellId, Player caster, boolean holy) {
    }

    private static Contracts activeContracts() {
        return resolve() ? contracts : null;
    }

    private static boolean active(Contracts expected) {
        return expected != null && state == ResolutionState.AVAILABLE && contracts == expected;
    }

    private static ResourceLocation spellId(Contracts resolved, Object spell) {
        return typedValue(resolved, resolved.spell().spellResource(), spell, ResourceLocation.class);
    }

    private static boolean isHolySchool(Contracts resolved, Object school) {
        return hasSchoolId(resolved, school, HOLY_SCHOOL);
    }

    private static boolean hasSchoolId(
            Contracts resolved,
            Object school,
            ResourceLocation expectedId
    ) {
        if (!resolved.spell().schoolType().isInstance(school)) {
            return false;
        }
        ResourceLocation id = typedValue(
                resolved, resolved.spell().schoolId(), school, ResourceLocation.class
        );
        return expectedId.equals(id);
    }

    private static int intValue(Contracts resolved, Method method, Object target, Object... arguments) {
        Number value = typedValue(resolved, method, target, Number.class, arguments);
        return value == null ? 0 : value.intValue();
    }

    private static float floatValue(Contracts resolved, Method method, Object target, Object... arguments) {
        Number value = typedValue(resolved, method, target, Number.class, arguments);
        return value == null ? 0f : value.floatValue();
    }

    private static boolean booleanValue(
            Contracts resolved, Method method, Object target, Object... arguments
    ) {
        Boolean value = typedValue(resolved, method, target, Boolean.class, arguments);
        return value != null && value;
    }

    private static Object requiredValue(
            Contracts resolved, Method method, Object target, Object... arguments
    ) {
        Object value = invoke(resolved, method, target, arguments);
        if (value == FAILED_INVOCATION) {
            return null;
        }
        if (value == null) {
            disable("validating " + method.toGenericString(),
                    new IllegalStateException("Required API method returned null"));
            return null;
        }
        return value;
    }

    private static <T> T typedValue(
            Contracts resolved,
            Method method,
            Object target,
            Class<T> expectedType,
            Object... arguments
    ) {
        Object value = requiredValue(resolved, method, target, arguments);
        if (value == null) {
            return null;
        }
        if (!expectedType.isInstance(value)) {
            disable("validating " + method.toGenericString(), new IllegalStateException(
                    "Expected " + expectedType.getTypeName() + " but received " + value.getClass().getTypeName()
            ));
            return null;
        }
        return expectedType.cast(value);
    }

    private static boolean invokeVoid(
            Contracts resolved, Method method, Object target, Object... arguments
    ) {
        return invoke(resolved, method, target, arguments) != FAILED_INVOCATION;
    }

    private static Object invoke(
            Contracts resolved, Method method, Object target, Object... arguments
    ) {
        if (!active(resolved)) {
            return FAILED_INVOCATION;
        }
        try {
            Object value = method.invoke(target, arguments);
            return active(resolved) ? value : FAILED_INVOCATION;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            disable("invoking " + method.toGenericString(), failure);
            return FAILED_INVOCATION;
        }
    }

    private static void disable(String operation, Throwable failure) {
        state = ResolutionState.FAILED;
        contracts = null;
        if (DIAGNOSTIC_REPORTED.compareAndSet(false, true)) {
            VampireSpellsAddon.LOGGER.error(
                    "Iron's Spells integration was disabled while " + operation
                            + ". All Iron's Spells-dependent behavior will fail closed.",
                    failure
            );
        }
    }

    private enum ResolutionState {
        UNRESOLVED,
        AVAILABLE,
        FAILED
    }
}
