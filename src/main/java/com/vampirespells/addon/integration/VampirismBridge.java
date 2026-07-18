package com.vampirespells.addon.integration;

import com.vampirespells.addon.VampireSpellsAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reflection-only access to the public Vampirism API.
 *
 * <p>The bridge resolves every used contract once. If resolution or a later
 * reflective call fails, the complete integration is disabled so callers do
 * not continue with a partially compatible API.</p>
 */
public final class VampirismBridge {

    private static final String VAMPIRISM_API = "de.teamlapen.vampirism.api.VampirismAPI";
    private static final String VAMPIRE_PLAYER =
            "de.teamlapen.vampirism.api.entity.player.vampire.IVampirePlayer";
    private static final String BLOOD_STATS =
            "de.teamlapen.vampirism.api.entity.player.vampire.IBloodStats";
    private static final String VAMPIRE_ENTITY =
            "de.teamlapen.vampirism.api.entity.vampire.IVampire";
    private static final String DRINK_BLOOD_CONTEXT =
            "de.teamlapen.vampirism.api.entity.player.vampire.IDrinkBloodContext";
    private static final String LAZY_OPTIONAL = "net.minecraftforge.common.util.LazyOptional";

    private static final Object RESOLUTION_LOCK = new Object();
    private static final AtomicBoolean DIAGNOSTIC_REPORTED = new AtomicBoolean();
    private static final VampireContext NOT_VAMPIRE = new VampireContext(null, null);

    private static volatile ResolutionState state = ResolutionState.UNRESOLVED;
    private static volatile Contracts contracts;

    private VampirismBridge() {
    }

    /**
     * Resolves and validates all Vampirism contracts used by this addon.
     * Subsequent calls return the result of the first attempt.
     */
    public static boolean resolve() {
        ResolutionState currentState = state;
        if (currentState != ResolutionState.UNRESOLVED) {
            return currentState == ResolutionState.AVAILABLE;
        }

        synchronized (RESOLUTION_LOCK) {
            if (state == ResolutionState.UNRESOLVED) {
                try {
                    contracts = Contracts.resolve();
                    state = ResolutionState.AVAILABLE;
                } catch (ReflectiveOperationException | LinkageError failure) {
                    disable("resolving public Vampirism API contracts", failure);
                } catch (RuntimeException failure) {
                    disable("validating public Vampirism API contracts", failure);
                }
            }
            return state == ResolutionState.AVAILABLE;
        }
    }

    public static boolean available() {
        return resolve();
    }

    public static VampireContext vampire(Player player) {
        if (player == null || !resolve()) {
            return NOT_VAMPIRE;
        }

        Contracts resolved = contracts;
        try {
            Object handle = resolved.playerLookup().find(player);
            if (handle == null || !resolved.vampirePlayerType().isInstance(handle)) {
                return NOT_VAMPIRE;
            }

            int level = invokeInt(resolved.getLevel(), handle);
            return level > 0 ? new VampireContext(resolved, handle) : NOT_VAMPIRE;
        } catch (ReflectiveOperationException | LinkageError failure) {
            disable("obtaining a vampire player context", failure);
        } catch (RuntimeException failure) {
            disable("validating a vampire player context", failure);
        }
        return NOT_VAMPIRE;
    }

    public static boolean isVampireEntity(LivingEntity entity) {
        if (entity == null || !resolve()) {
            return false;
        }

        Contracts resolved = contracts;
        try {
            return resolved.vampireEntityType().isInstance(entity);
        } catch (LinkageError failure) {
            disable("checking a Vampirism entity marker", failure);
        } catch (RuntimeException failure) {
            disable("validating a Vampirism entity marker", failure);
        }
        return false;
    }

    private static boolean active(Contracts expected) {
        return expected != null && state == ResolutionState.AVAILABLE && contracts == expected;
    }

    private static int invokeInt(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        Object value = method.invoke(target, arguments);
        if (value instanceof Integer result) {
            return result;
        }
        throw new ReflectiveOperationException("Expected int from " + method.toGenericString());
    }

    private static boolean invokeBoolean(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        Object value = method.invoke(target, arguments);
        if (value instanceof Boolean result) {
            return result;
        }
        throw new ReflectiveOperationException("Expected boolean from " + method.toGenericString());
    }

    private static void disable(String operation, Throwable failure) {
        contracts = null;
        state = ResolutionState.FAILED;
        if (DIAGNOSTIC_REPORTED.compareAndSet(false, true)) {
            VampireSpellsAddon.LOGGER.error(
                    "Vampirism integration was disabled while " + operation
                            + ". All Vampirism-dependent behavior will fail closed.",
                    failure
            );
        }
    }

    public static final class VampireContext {

        private final Contracts resolved;
        private final Object handle;

        private VampireContext(Contracts resolved, Object handle) {
            this.resolved = resolved;
            this.handle = handle;
        }

        public boolean isVampire() {
            return handle != null && active(resolved);
        }

        public int bloodLevel() {
            if (!isVampire()) {
                return 0;
            }

            try {
                return readBloodLevel();
            } catch (ReflectiveOperationException | LinkageError failure) {
                disable("reading a vampire player's blood level", failure);
            } catch (RuntimeException failure) {
                disable("validating a vampire player's blood level", failure);
            }
            return 0;
        }

        public int maxBlood() {
            if (!isVampire()) {
                return 0;
            }

            try {
                return readMaxBlood();
            } catch (ReflectiveOperationException | LinkageError failure) {
                disable("reading a vampire player's maximum blood", failure);
            } catch (RuntimeException failure) {
                disable("validating a vampire player's maximum blood", failure);
            }
            return 0;
        }

        /**
         * Atomically consumes the full cost or leaves the blood level unchanged.
         */
        public boolean consumeBlood(int cost) {
            if (!isVampire() || cost <= 0) {
                return false;
            }

            try {
                return invokeBoolean(resolved.useBlood(), handle, cost, false);
            } catch (ReflectiveOperationException | LinkageError failure) {
                disable("consuming vampire blood", failure);
            } catch (RuntimeException failure) {
                disable("validating a vampire blood consumption result", failure);
            }
            return false;
        }

        /**
         * Restores at most the currently free blood capacity and returns the
         * observed positive change in blood level.
         */
        public int restoreBlood(int requested, LivingEntity target, float saturation) {
            if (!isVampire() || requested <= 0 || !Float.isFinite(saturation) || saturation < 0f) {
                return 0;
            }

            try {
                int before = readBloodLevel();
                int maximum = readMaxBlood();
                long freeCapacity = Math.max(0L, (long) maximum - before);
                int amount = (int) Math.min((long) requested, freeCapacity);
                if (amount <= 0) {
                    return 0;
                }

                Object drinkContext = createDrinkContext(resolved, target);
                resolved.drinkBlood().invoke(handle, amount, saturation, false, drinkContext);
                if (!active(resolved)) {
                    return 0;
                }

                long restored = (long) readBloodLevel() - before;
                return restored > 0L ? (int) Math.min(restored, Integer.MAX_VALUE) : 0;
            } catch (ReflectiveOperationException | LinkageError failure) {
                disable("restoring vampire blood", failure);
            } catch (RuntimeException failure) {
                disable("validating a vampire blood restoration result", failure);
            }
            return 0;
        }

        private int readBloodLevel() throws ReflectiveOperationException {
            return invokeInt(resolved.getBloodLevel(), handle);
        }

        private int readMaxBlood() throws ReflectiveOperationException {
            Object bloodStats = resolved.getBloodStats().invoke(handle);
            if (bloodStats == null || !resolved.bloodStatsType().isInstance(bloodStats)) {
                throw new ReflectiveOperationException(
                        "IVampirePlayer#getBloodStats returned an incompatible value"
                );
            }
            return invokeInt(resolved.getMaxBlood(), bloodStats);
        }
    }

    private static Object createDrinkContext(Contracts resolved, LivingEntity target) {
        return Proxy.newProxyInstance(
                resolved.drinkContextType().getClassLoader(),
                new Class<?>[]{resolved.drinkContextType()},
                (proxy, method, arguments) -> {
                    if (method.equals(resolved.getEntity())) {
                        return Optional.ofNullable(target);
                    }
                    if (method.equals(resolved.getStack())
                            || method.equals(resolved.getBlockState())
                            || method.equals(resolved.getBlockPos())) {
                        return Optional.empty();
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> proxy.getClass().getName() + "@"
                                    + Integer.toHexString(System.identityHashCode(proxy));
                            default -> unsupportedContextMethod(method);
                        };
                    }
                    return unsupportedContextMethod(method);
                }
        );
    }

    private static Object unsupportedContextMethod(Method method) {
        UnsupportedOperationException failure = new UnsupportedOperationException(
                "Unsupported IDrinkBloodContext API method: " + method.toGenericString()
        );
        disable("serving a changed IDrinkBloodContext contract", failure);
        throw failure;
    }

    private record Contracts(
            Class<?> vampirePlayerType,
            Class<?> bloodStatsType,
            Class<?> vampireEntityType,
            Class<?> drinkContextType,
            PlayerLookup playerLookup,
            Method getLevel,
            Method getBloodLevel,
            Method getBloodStats,
            Method getMaxBlood,
            Method useBlood,
            Method drinkBlood,
            Method getEntity,
            Method getStack,
            Method getBlockState,
            Method getBlockPos
    ) {

        private static Contracts resolve() throws ReflectiveOperationException {
            ClassLoader loader = VampirismBridge.class.getClassLoader();
            Class<?> apiType = Class.forName(VAMPIRISM_API, false, loader);
            Class<?> vampirePlayerType = requireInterface(VAMPIRE_PLAYER);
            Class<?> bloodStatsType = requireInterface(BLOOD_STATS);
            Class<?> vampireEntityType = requireInterface(VAMPIRE_ENTITY);
            Class<?> drinkContextType = requireInterface(DRINK_BLOOD_CONTEXT);

            if (!vampireEntityType.isAssignableFrom(vampirePlayerType)) {
                throw new ReflectiveOperationException(
                        VAMPIRE_PLAYER + " no longer extends " + VAMPIRE_ENTITY
                );
            }

            PlayerLookup playerLookup = resolvePlayerLookup(apiType, vampirePlayerType);

            Method getLevel = requireMethod(vampirePlayerType, "getLevel", int.class);
            Method getBloodLevel = requireMethod(vampirePlayerType, "getBloodLevel", int.class);
            Method getBloodStats = requireMethod(vampirePlayerType, "getBloodStats", bloodStatsType);
            Method getMaxBlood = requireMethod(bloodStatsType, "getMaxBlood", int.class);
            Method useBlood = requireMethod(vampireEntityType, "useBlood", boolean.class,
                    int.class, boolean.class);
            Method drinkBlood = requireMethod(vampireEntityType, "drinkBlood", void.class,
                    int.class, float.class, boolean.class, drinkContextType);

            Method getEntity = requireMethod(drinkContextType, "getEntity", Optional.class);
            Method getStack = requireMethod(drinkContextType, "getStack", Optional.class);
            Method getBlockState = requireMethod(drinkContextType, "getBlockState", Optional.class);
            Method getBlockPos = requireMethod(drinkContextType, "getBlockPos", Optional.class);

            requireInstance(
                    getLevel,
                    getBloodLevel,
                    getBloodStats,
                    getMaxBlood,
                    useBlood,
                    drinkBlood,
                    getEntity,
                    getStack,
                    getBlockState,
                    getBlockPos
            );

            return new Contracts(
                    vampirePlayerType,
                    bloodStatsType,
                    vampireEntityType,
                    drinkContextType,
                    playerLookup,
                    getLevel,
                    getBloodLevel,
                    getBloodStats,
                    getMaxBlood,
                    useBlood,
                    drinkBlood,
                    getEntity,
                    getStack,
                    getBlockState,
                    getBlockPos
            );
        }

        private static PlayerLookup resolvePlayerLookup(
                Class<?> apiType,
                Class<?> vampirePlayerType
        ) throws ReflectiveOperationException {
            NoSuchMethodException directLookupFailure;
            try {
                Method directLookup = requireMethod(
                        apiType, "vampirePlayer", vampirePlayerType, Player.class
                );
                requireStatic(directLookup);
                return new PlayerLookup(directLookup, null);
            } catch (NoSuchMethodException failure) {
                directLookupFailure = failure;
            }

            try {
                Class<?> lazyOptionalType = Class.forName(
                        LAZY_OPTIONAL,
                        false,
                        VampirismBridge.class.getClassLoader()
                );
                Method legacyLookup = requireMethod(
                        apiType, "getVampirePlayer", lazyOptionalType, Player.class
                );
                requireStatic(legacyLookup);
                Method resolveOptional = requireMethod(
                        lazyOptionalType, "resolve", Optional.class
                );
                requireInstance(resolveOptional);
                return new PlayerLookup(legacyLookup, resolveOptional);
            } catch (ReflectiveOperationException legacyFailure) {
                legacyFailure.addSuppressed(directLookupFailure);
                throw legacyFailure;
            }
        }

        private static Class<?> requireInterface(String className) throws ReflectiveOperationException {
            Class<?> type = Class.forName(
                    className,
                    false,
                    VampirismBridge.class.getClassLoader()
            );
            if (!type.isInterface()) {
                throw new ReflectiveOperationException(className + " is no longer an interface");
            }
            return type;
        }

        private static Method requireMethod(
                Class<?> owner,
                String name,
                Class<?> expectedReturnType,
                Class<?>... parameterTypes
        ) throws ReflectiveOperationException {
            Method method = owner.getMethod(name, parameterTypes);
            if (!expectedReturnType.isAssignableFrom(method.getReturnType())) {
                throw new ReflectiveOperationException(
                        method.toGenericString() + " has incompatible return type; expected "
                                + expectedReturnType.getTypeName()
                );
            }
            return method;
        }

        private static void requireInstance(Method... methods) throws ReflectiveOperationException {
            for (Method method : methods) {
                if (Modifier.isStatic(method.getModifiers())) {
                    throw new ReflectiveOperationException(
                            method.toGenericString() + " unexpectedly became static"
                    );
                }
            }
        }

        private static void requireStatic(Method method) throws ReflectiveOperationException {
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new ReflectiveOperationException(method.toGenericString() + " is no longer static");
            }
        }
    }

    private record PlayerLookup(Method apiMethod, Method resolveOptional) {

        private Object find(Player player) throws ReflectiveOperationException {
            Object result = apiMethod.invoke(null, player);
            if (resolveOptional == null) {
                return result;
            }
            if (result == null || !resolveOptional.getDeclaringClass().isInstance(result)) {
                throw new ReflectiveOperationException(
                        apiMethod.toGenericString() + " returned an incompatible LazyOptional"
                );
            }

            Object resolved = resolveOptional.invoke(result);
            if (resolved instanceof Optional<?> optional) {
                return optional.orElse(null);
            }
            throw new ReflectiveOperationException(
                    resolveOptional.toGenericString() + " returned an incompatible value"
            );
        }
    }

    private enum ResolutionState {
        UNRESOLVED,
        AVAILABLE,
        FAILED
    }
}
