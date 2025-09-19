package com.vampirespells.addon.event;

import com.vampirespells.addon.VampireSpellsAddon;
import com.vampirespells.addon.config.AddonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@EventBusSubscriber(modid = VampireSpellsAddon.MOD_ID)
public class SpellEventHandler {

    private static final ResourceLocation RAY_OF_SIPHONING_ID = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ray_of_siphoning");
    private static final ResourceLocation DEVOUR_ID = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "devour");
    private static final Set<ResourceLocation> BLOOD_COST_SPELLS = Set.of(
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "wither_skull"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "sacrifice"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "raise_dead"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "heartstop"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "blood_step"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "blood_slash"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "blood_needles"),
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "acupuncture")
    );

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, BloodCastDecision>> BLOOD_CAST_DECISIONS = new ConcurrentHashMap<>();

    static {
        initializeSpellListeners();
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        try {
            Object source = event.getSource();
            Class<?> spellDamageSourceClass = resolveClass("io.redspace.ironsspellbooks.damage.SpellDamageSource");
            if (spellDamageSourceClass == null || !spellDamageSourceClass.isInstance(source)) {
                return;
            }

            Method spellMethod = cachedMethod(spellDamageSourceClass, "spell");
            Object spell = spellMethod.invoke(source);

            Method getSpellResourceMethod = cachedMethod(spell.getClass(), "getSpellResource");
            ResourceLocation spellResource = (ResourceLocation) getSpellResourceMethod.invoke(spell);

            if (spellResource.equals(RAY_OF_SIPHONING_ID)) {
                handleRayOfSiphoning(event, spellDamageSourceClass, source);
            } else if (spellResource.equals(DEVOUR_ID)) {
                handleDevour(event, spellDamageSourceClass, source);
            }
        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Could not process spell damage hook: {}", e.getMessage());
        }
    }

    private static void handleRayOfSiphoning(LivingDamageEvent.Pre event, Class<?> spellDamageSourceClass, Object source) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Player caster = getCasterFromSource(spellDamageSourceClass, source);
        if (caster == null) {
            return;
        }

        VampireContext vampireContext = getVampireContext(caster);
        if (!vampireContext.isVampire()) {
            return;
        }

        LivingEntity target = event.getEntity();
        float damageAmount = Math.max(event.getNewDamage(), 0f);
        if (damageAmount <= 0f) {
            return;
        }

        double restoreMultiplier = Math.max(0d, AddonConfig.RAY_BLOOD_RESTORE_MULTIPLIER.get());
        double saturation = Math.max(0d, AddonConfig.RAY_BLOOD_SATURATION.get());

        if (restoreMultiplier <= 0d) {
            return;
        }

        int bloodAmount = Math.max(1, Math.round(damageAmount * (float) restoreMultiplier));
        vampireContext.drinkBlood(bloodAmount, target, (float) saturation);

        VampireSpellsAddon.LOGGER.debug("Ray of Siphoning: vampire {} gained {} blood from {}", caster.getName().getString(), bloodAmount, target.getName().getString());
    }

    private static void handleDevour(LivingDamageEvent.Pre event, Class<?> spellDamageSourceClass, Object source) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Player caster = getCasterFromSource(spellDamageSourceClass, source);
        if (caster == null) {
            return;
        }

        VampireContext vampireContext = getVampireContext(caster);
        if (!vampireContext.isVampire()) {
            return;
        }

        LivingEntity target = event.getEntity();
        float damageAmount = Math.max(event.getNewDamage(), 0f);
        if (damageAmount <= 0f) {
            return;
        }

        double restoreMultiplier = Math.max(0d, AddonConfig.DEVOUR_BLOOD_RESTORE_MULTIPLIER.get());
        double saturation = Math.max(0d, AddonConfig.DEVOUR_BLOOD_SATURATION.get());

        if (restoreMultiplier <= 0d) {
            return;
        }

        int bloodAmount = Math.max(1, Math.round(damageAmount * (float) restoreMultiplier));
        vampireContext.drinkBlood(bloodAmount, target, (float) saturation);

        VampireSpellsAddon.LOGGER.debug("Devour: vampire {} gained {} blood from {}", caster.getName().getString(), bloodAmount, target.getName().getString());
    }

    private static Player getCasterFromSource(Class<?> spellDamageSourceClass, Object source) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method getEntityMethod = cachedMethod(spellDamageSourceClass, "getEntity");
        Object attacker = getEntityMethod.invoke(source);
        return attacker instanceof Player player ? player : null;
    }

    private static void initializeSpellListeners() {
        registerSpellListener(
                "io.redspace.ironsspellbooks.api.events.SpellPreCastEvent",
                SpellEventHandler::onSpellPreCast
        );
        registerSpellListener(
                "io.redspace.ironsspellbooks.api.events.SpellOnCastEvent",
                SpellEventHandler::onSpellOnCast
        );
        registerSpellListener(
                "io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent$Pre",
                SpellEventHandler::onSpellCooldownPre
        );
    }

    private static void registerSpellListener(String className, Consumer<Object> handler) {
        try {
            Class<?> eventClass = resolveClass(className);
            if (eventClass == null) {
                VampireSpellsAddon.LOGGER.warn("Could not register listener for {}: class not found", className);
                return;
            }

            Method addListener = findAddListenerMethod();
            if (addListener != null) {
                addListener.invoke(NeoForge.EVENT_BUS, EventPriority.NORMAL, false, eventClass, handler);
                VampireSpellsAddon.LOGGER.debug("Registered reflective listener for {}", className);
                return;
            }

            Method fallback = findGenericAddListenerMethod();
            if (fallback != null) {
                Consumer<Object> wrapped = event -> {
                    if (eventClass.isInstance(event)) {
                        handler.accept(event);
                    }
                };
                fallback.invoke(NeoForge.EVENT_BUS, wrapped);
                VampireSpellsAddon.LOGGER.debug("Registered fallback listener for {}", className);
                return;
            }

            VampireSpellsAddon.LOGGER.warn("Failed to register listener for {}: no suitable addListener overload", className);
        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Failed to register listener for {}: {}", className, e.getMessage());
        }
    }

    private static Method findAddListenerMethod() {
        for (Method method : NeoForge.EVENT_BUS.getClass().getMethods()) {
            if (!"addListener".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 4 && params[0] == EventPriority.class && params[1] == boolean.class && params[2] == Class.class && Consumer.class.isAssignableFrom(params[3])) {
                return method;
            }
        }
        return null;
    }

    private static Method findGenericAddListenerMethod() {
        for (Method method : NeoForge.EVENT_BUS.getClass().getMethods()) {
            if (!"addListener".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && Consumer.class.isAssignableFrom(params[0])) {
                return method;
            }
        }
        return null;
    }

    private static void onSpellPreCast(Object event) {
        if (!(event instanceof PlayerEvent playerEvent)) {
            return;
        }

        Player caster = playerEvent.getEntity();
        VampireContext vampireContext = getVampireContext(caster);
        if (!vampireContext.isVampire()) {
            return;
        }

        try {
            Method getSpellIdMethod = cachedMethod(event.getClass(), "getSpellId");
            Method getSpellLevelMethod = cachedMethod(event.getClass(), "getSpellLevel");

            String spellId = (String) getSpellIdMethod.invoke(event);
            ResourceLocation spellResource = ResourceLocation.parse(spellId);

            if (!BLOOD_COST_SPELLS.contains(spellResource)) {
                clearDecision(caster, spellId);
                return;
            }

            int spellLevel = (Integer) getSpellLevelMethod.invoke(event);
            int bloodCost = calculateBloodCost(spellResource, spellLevel);

            BloodCastDecision decision = decideBloodUsage(vampireContext, bloodCost);
            storeDecision(caster, spellId, decision);

            VampireSpellsAddon.LOGGER.debug(
                    "Pre-cast decision for {} by {}: useBlood={}, cost={}, cooldownMultiplier={}",
                    spellResource,
                    caster.getName().getString(),
                    decision.useBlood(),
                    decision.bloodCost(),
                    decision.cooldownMultiplier()
            );
        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Failed to evaluate SpellPreCastEvent: {}", e.getMessage());
        }
    }

    private static void onSpellOnCast(Object event) {
        if (!(event instanceof PlayerEvent playerEvent)) {
            return;
        }

        Player caster = playerEvent.getEntity();
        VampireContext vampireContext = getVampireContext(caster);

        try {
            Method getSpellIdMethod = cachedMethod(event.getClass(), "getSpellId");
            Method getSpellLevelMethod = cachedMethod(event.getClass(), "getSpellLevel");

            String spellId = (String) getSpellIdMethod.invoke(event);
            ResourceLocation spellResource = ResourceLocation.parse(spellId);

            if (spellResource.equals(DEVOUR_ID)) {
                adjustDevourManaCost(event);
                return;
            }

            if (!vampireContext.isVampire() || !BLOOD_COST_SPELLS.contains(spellResource)) {
                clearDecision(caster, spellId);
                return;
            }

            int spellLevel = (Integer) getSpellLevelMethod.invoke(event);
            int bloodCost = calculateBloodCost(spellResource, spellLevel);

            BloodCastDecision decision = getDecision(caster, spellId);
            if (decision == null || decision.bloodCost() != bloodCost) {
                decision = decideBloodUsage(vampireContext, bloodCost);
                storeDecision(caster, spellId, decision);
            }

            if (decision.useBlood() && bloodCost > 0) {
                if (!vampireContext.consumeBlood(bloodCost)) {
                    VampireSpellsAddon.LOGGER.debug("Failed to consume blood cost {} for spell {} by vampire {}",
                            bloodCost, spellResource, caster.getName().getString());
                    BloodCastDecision fallback = decideBloodUsage(vampireContext, 0);
                    storeDecision(caster, spellId, fallback);
                } else {
                    VampireSpellsAddon.LOGGER.debug("Consumed {} blood for spell {} by vampire {} (remaining {})",
                            bloodCost, spellResource, caster.getName().getString(), vampireContext.getBloodLevel());
                }
            } else {
                VampireSpellsAddon.LOGGER.debug("Skipped blood cost for spell {} by vampire {} (current blood {} / max {})",
                        spellResource, caster.getName().getString(), vampireContext.getBloodLevel(), vampireContext.getMaxBlood());
            }
        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Failed to process SpellOnCastEvent: {}", e.getMessage());
        }
    }

    private static void adjustDevourManaCost(Object event) {
        try {
            Method getOriginalManaCost = cachedMethod(event.getClass(), "getOriginalManaCost");
            Method getManaCost = cachedMethod(event.getClass(), "getManaCost");
            Method setManaCost = cachedMethod(event.getClass(), "setManaCost", int.class);

            int original = (Integer) getOriginalManaCost.invoke(event);
            int current = (Integer) getManaCost.invoke(event);
            double multiplier = Math.max(0d, AddonConfig.DEVOUR_MANA_MULTIPLIER.get());
            int desired = (int) Math.round(current * multiplier);
            if (multiplier > 0 && desired <= 0) {
                desired = 1;
            }

            if (desired != current) {
                setManaCost.invoke(event, desired);
                VampireSpellsAddon.LOGGER.debug("Adjusted Devour mana cost to {} (original {}, prior {})", desired, original, current);
            }
        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Failed to adjust Devour mana cost: {}", e.getMessage());
        }
    }

    private static int calculateBloodCost(ResourceLocation spellId, int spellLevel) {
        try {
            Class<?> spellRegistryClass = resolveClass("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            if (spellRegistryClass == null) {
                return 0;
            }
            Method getSpellMethod = cachedMethod(spellRegistryClass, "getSpell", ResourceLocation.class);
            Object spell = getSpellMethod.invoke(null, spellId);
            if (spell == null) {
                return 0;
            }

            Method getManaCostMethod = cachedMethod(spell.getClass(), "getManaCost", int.class);
            int manaCost = (Integer) getManaCostMethod.invoke(spell, spellLevel);

            if (manaCost <= 0) {
                return 0;
            }
            double ratio = Math.max(0d, computeBloodRatio(manaCost));
            if (ratio <= 0d) {
                return 0;
            }

            return Math.max(1, (int) Math.ceil(manaCost * ratio));
        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Failed to calculate blood cost for {}: {}", spellId, e.getMessage());
            return 0;
        }
    }

    private static double computeBloodRatio(int manaCost) {
        double floorMana = Math.max(0d, AddonConfig.BLOOD_COST_MANA_FLOOR.get());
        double ceilingMana = Math.max(floorMana, AddonConfig.BLOOD_COST_MANA_CEILING.get());
        double minRatio = Math.max(0d, AddonConfig.BLOOD_COST_RATIO_MIN.get());
        double maxRatio = Math.max(0d, AddonConfig.BLOOD_COST_RATIO_MAX.get());

        if (ceilingMana <= floorMana) {
            return manaCost >= ceilingMana ? maxRatio : minRatio;
        }

        double normalized = clamp((manaCost - floorMana) / (ceilingMana - floorMana), 0d, 1d);
        return minRatio + (maxRatio - minRatio) * normalized;
    }

    private static VampireContext getVampireContext(Player player) {
        try {
            Class<?> apiClass = resolveClass("de.teamlapen.vampirism.api.VampirismAPI");
            if (apiClass == null) {
                return VampireContext.notVampire();
            }
            Method vampirePlayerMethod = cachedMethod(apiClass, "vampirePlayer", Player.class);
            Object vampirePlayer = vampirePlayerMethod.invoke(null, player);
            if (vampirePlayer == null) {
                return VampireContext.notVampire();
            }

            Method getLevelMethod = cachedMethod(vampirePlayer.getClass(), "getLevel");
            int level = (Integer) getLevelMethod.invoke(vampirePlayer);
            if (level <= 0) {
                return VampireContext.notVampire();
            }

            return new VampireContext(player, vampirePlayer);
        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Failed to fetch vampire context: {}", e.getMessage());
            return VampireContext.notVampire();
        }
    }

    private static BloodCastDecision decideBloodUsage(VampireContext vampireContext, int bloodCost) {
        double thresholdFraction = clamp(AddonConfig.HIGH_BLOOD_THRESHOLD_FRACTION.get(), 0d, 1d);
        int maxBlood = vampireContext.getMaxBlood();
        int currentBlood = vampireContext.getBloodLevel();

        boolean highBlood = maxBlood > 0 && currentBlood >= Math.round(maxBlood * thresholdFraction);
        boolean useBlood = highBlood && bloodCost > 0 && vampireContext.hasBlood(bloodCost);

        double highMultiplier = Math.max(0d, AddonConfig.HIGH_BLOOD_COOLDOWN_MULTIPLIER.get());
        double lowMultiplier = Math.max(0d, AddonConfig.LOW_BLOOD_COOLDOWN_MULTIPLIER.get());
        double cooldownMultiplier = highBlood ? highMultiplier : lowMultiplier;

        if (!useBlood) {
            bloodCost = Math.max(0, bloodCost);
        }

        return new BloodCastDecision(useBlood, bloodCost, cooldownMultiplier);
    }

    private static void onSpellCooldownPre(Object event) {
        try {
            Method getEntityMethod = cachedMethod(event.getClass(), "getEntity");
            Object entityObj = getEntityMethod.invoke(event);
            if (!(entityObj instanceof Player caster)) {
                return;
            }

            Method getSpellMethod = cachedMethod(event.getClass(), "getSpell");
            Object spell = getSpellMethod.invoke(event);
            if (spell == null) {
                return;
            }

            Method getSpellResourceMethod = cachedMethod(spell.getClass(), "getSpellResource");
            ResourceLocation spellResource = (ResourceLocation) getSpellResourceMethod.invoke(spell);
            String spellId = spellResource.toString();

            BloodCastDecision decision = removeDecision(caster, spellId);
            if (decision == null) {
                return;
            }

            Method getCooldownMethod = cachedMethod(event.getClass(), "getEffectiveCooldown");
            Method setCooldownMethod = cachedMethod(event.getClass(), "setEffectiveCooldown", int.class);

            int current = (Integer) getCooldownMethod.invoke(event);
            double multiplier = Math.max(0d, decision.cooldownMultiplier());
            int adjusted = (int) Math.round(current * multiplier);
            if (multiplier > 0d && adjusted <= 0) {
                adjusted = 1;
            }

            setCooldownMethod.invoke(event, Math.max(0, adjusted));

            VampireSpellsAddon.LOGGER.debug("Adjusted cooldown for {} by {} with multiplier {} ({} -> {})",
                    spellResource,
                    caster.getName().getString(),
                    multiplier,
                    current,
                    Math.max(0, adjusted));
        } catch (Exception e) {
            VampireSpellsAddon.LOGGER.warn("Failed to adjust cooldown: {}", e.getMessage());
        }
    }

    private static void storeDecision(Player player, String spellId, BloodCastDecision decision) {
        BLOOD_CAST_DECISIONS
                .computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                .put(spellId, decision);
    }

    private static BloodCastDecision getDecision(Player player, String spellId) {
        Map<String, BloodCastDecision> decisions = BLOOD_CAST_DECISIONS.get(player.getUUID());
        return decisions != null ? decisions.get(spellId) : null;
    }

    private static BloodCastDecision removeDecision(Player player, String spellId) {
        Map<String, BloodCastDecision> decisions = BLOOD_CAST_DECISIONS.get(player.getUUID());
        if (decisions == null) {
            return null;
        }
        BloodCastDecision removed = decisions.remove(spellId);
        if (decisions.isEmpty()) {
            BLOOD_CAST_DECISIONS.remove(player.getUUID());
        }
        return removed;
    }

    private static void clearDecision(Player player, String spellId) {
        removeDecision(player, spellId);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Method cachedMethod(Class<?> targetClass, String name, Class<?>... params) throws NoSuchMethodException {
        StringBuilder keyBuilder = new StringBuilder(targetClass.getName()).append('#').append(name).append('(');
        for (Class<?> param : params) {
            keyBuilder.append(param == null ? "null" : param.getName()).append(',');
        }
        keyBuilder.append(')');
        String key = keyBuilder.toString();

        Method method = METHOD_CACHE.get(key);
        if (method != null) {
            return method;
        }

        method = targetClass.getMethod(name, params);
        method.setAccessible(true);
        METHOD_CACHE.put(key, method);
        return method;
    }

    private static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private record BloodCastDecision(boolean useBlood, int bloodCost, double cooldownMultiplier) {}

    private record VampireContext(Player player, Object handle) {

        static VampireContext notVampire() {
            return new VampireContext(null, null);
        }

        boolean isVampire() {
            return handle != null;
        }

        int getBloodLevel() {
            if (!isVampire()) {
                return 0;
            }
            try {
                Method getBloodLevelMethod = cachedMethod(handle.getClass(), "getBloodLevel");
                return (Integer) getBloodLevelMethod.invoke(handle);
            } catch (Exception e) {
                VampireSpellsAddon.LOGGER.warn("Failed to query vampire blood level: {}", e.getMessage());
                return 0;
            }
        }

        int getMaxBlood() {
            if (!isVampire()) {
                return 0;
            }
            try {
                Method getBloodStatsMethod = cachedMethod(handle.getClass(), "getBloodStats");
                Object stats = getBloodStatsMethod.invoke(handle);
                if (stats == null) {
                    return 0;
                }
                Method getMaxBloodMethod = cachedMethod(stats.getClass(), "getMaxBlood");
                return (Integer) getMaxBloodMethod.invoke(stats);
            } catch (Exception e) {
                VampireSpellsAddon.LOGGER.warn("Failed to query vampire max blood: {}", e.getMessage());
                return 0;
            }
        }

        boolean hasBlood(int cost) {
            return getBloodLevel() >= cost;
        }

        boolean consumeBlood(int cost) {
            if (!isVampire() || cost <= 0) {
                return false;
            }
            try {
                Method useBlood = cachedMethod(handle.getClass(), "useBlood", int.class, boolean.class);
                Object result = useBlood.invoke(handle, cost, false);
                return result instanceof Boolean bool ? bool : false;
            } catch (Exception e) {
                VampireSpellsAddon.LOGGER.warn("Failed to consume vampire blood: {}", e.getMessage());
                return false;
            }
        }

        void drinkBlood(int amount, LivingEntity target, float saturation) {
            if (!isVampire() || amount <= 0) {
                return;
            }
            try {
                Class<?> contextClass = resolveClass("de.teamlapen.vampirism.api.entity.player.vampire.IDrinkBloodContext");
                if (contextClass == null) {
                    return;
                }

                Object context = Proxy.newProxyInstance(
                        contextClass.getClassLoader(),
                        new Class[]{contextClass},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getEntity" -> Optional.ofNullable(target);
                            case "getStack", "getBlockState", "getBlockPos" -> Optional.empty();
                            default -> null;
                        }
                );

                Method drinkBlood;
                try {
                    drinkBlood = cachedMethod(handle.getClass(), "drinkBlood", int.class, float.class, boolean.class, contextClass);
                    drinkBlood.invoke(handle, amount, saturation, false, context);
                } catch (NoSuchMethodException missingFourArg) {
                    drinkBlood = cachedMethod(handle.getClass(), "drinkBlood", int.class, float.class, contextClass);
                    drinkBlood.invoke(handle, amount, saturation, context);
                }
            } catch (Exception e) {
                VampireSpellsAddon.LOGGER.warn("Failed to restore vampire blood: {}", e.getMessage());
            }
        }
    }
}
