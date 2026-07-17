package com.vampirespells.addon.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Stores delayed blood-cast decisions in NeoForge's persisted player data. */
final class PersistentBloodDecisionStore {

    private static final int SCHEMA = 1;
    private static final String ADDON_DATA_KEY = "vampire_spells_addon";
    private static final String DECISIONS_KEY = "blood_cast_decisions";
    private static final String SCHEMA_KEY = "schema";
    private static final String SPENT_BLOOD_KEY = "spent_blood";
    private static final String BLOOD_COST_KEY = "blood_cost";
    private static final String COOLDOWN_MULTIPLIER_KEY = "cooldown_multiplier";
    private static final String REMAINING_TICKS_KEY = "remaining_ticks";

    private PersistentBloodDecisionStore() {
    }

    static void store(
            CompoundTag persistedPlayerData,
            ResourceLocation spellId,
            CastStateTracker.BloodCastDecision decision,
            int lifetimeTicks
    ) {
        if (lifetimeTicks <= 0) {
            return;
        }

        CompoundTag addonData = getOrCreateCompound(persistedPlayerData, ADDON_DATA_KEY);
        CompoundTag decisions = getOrCreateCompound(addonData, DECISIONS_KEY);
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(SCHEMA_KEY, SCHEMA);
        encoded.putBoolean(SPENT_BLOOD_KEY, decision.spentBlood());
        encoded.putInt(BLOOD_COST_KEY, decision.bloodCost());
        encoded.putDouble(COOLDOWN_MULTIPLIER_KEY, decision.cooldownMultiplier());
        encoded.putInt(REMAINING_TICKS_KEY, lifetimeTicks);
        decisions.put(spellId.toString(), encoded);
    }

    static CastStateTracker.BloodCastDecision remove(
            CompoundTag persistedPlayerData,
            ResourceLocation spellId
    ) {
        CompoundTag addonData = existingCompound(persistedPlayerData, ADDON_DATA_KEY);
        CompoundTag decisions = existingCompound(addonData, DECISIONS_KEY);
        CompoundTag encoded = existingCompound(decisions, spellId.toString());
        if (decisions == null || encoded == null) {
            return null;
        }

        decisions.remove(spellId.toString());
        removeEmptyContainers(persistedPlayerData, addonData, decisions);
        if (!isValid(encoded)) {
            return null;
        }
        return new CastStateTracker.BloodCastDecision(
                encoded.getBoolean(SPENT_BLOOD_KEY),
                encoded.getInt(BLOOD_COST_KEY),
                encoded.getDouble(COOLDOWN_MULTIPLIER_KEY)
        );
    }

    static void tick(CompoundTag persistedPlayerData) {
        CompoundTag addonData = existingCompound(persistedPlayerData, ADDON_DATA_KEY);
        CompoundTag decisions = existingCompound(addonData, DECISIONS_KEY);
        if (decisions == null) {
            return;
        }

        for (String spellId : Set.copyOf(decisions.getAllKeys())) {
            CompoundTag encoded = existingCompound(decisions, spellId);
            if (!isValid(encoded) || encoded.getInt(REMAINING_TICKS_KEY) <= 1) {
                decisions.remove(spellId);
            } else {
                encoded.putInt(REMAINING_TICKS_KEY, encoded.getInt(REMAINING_TICKS_KEY) - 1);
            }
        }
        removeEmptyContainers(persistedPlayerData, addonData, decisions);
    }

    static void clear(CompoundTag persistedPlayerData) {
        persistedPlayerData.remove(ADDON_DATA_KEY);
    }

    private static boolean isValid(CompoundTag encoded) {
        if (encoded == null || encoded.getInt(SCHEMA_KEY) != SCHEMA) {
            return false;
        }
        double cooldownMultiplier = encoded.getDouble(COOLDOWN_MULTIPLIER_KEY);
        return encoded.getInt(BLOOD_COST_KEY) >= 0
                && encoded.getInt(REMAINING_TICKS_KEY) > 0
                && Double.isFinite(cooldownMultiplier)
                && cooldownMultiplier >= 0d;
    }

    private static CompoundTag getOrCreateCompound(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            parent.put(key, new CompoundTag());
        }
        return parent.getCompound(key);
    }

    private static CompoundTag existingCompound(CompoundTag parent, String key) {
        return parent != null && parent.contains(key, Tag.TAG_COMPOUND)
                ? parent.getCompound(key)
                : null;
    }

    private static void removeEmptyContainers(
            CompoundTag persistedPlayerData,
            CompoundTag addonData,
            CompoundTag decisions
    ) {
        if (decisions.isEmpty()) {
            addonData.remove(DECISIONS_KEY);
        }
        if (addonData.isEmpty()) {
            persistedPlayerData.remove(ADDON_DATA_KEY);
        }
    }
}
