package com.vampirespells.addon.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CastStateTracker {

    private static final float HEAL_AMOUNT_EPSILON = 0.0001f;

    private final Map<UUID, Map<ResourceLocation, TimedBloodDecision>> bloodDecisions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingHeal> pendingHeals = new ConcurrentHashMap<>();

    void storeBloodDecision(
            Player player,
            ResourceLocation spellId,
            BloodCastDecision decision,
            int serverTick,
            int lifetimeTicks
    ) {
        long expiresAtTick = (long) serverTick + Math.max(0, lifetimeTicks);
        bloodDecisions
                .computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>())
                .put(spellId, new TimedBloodDecision(decision, expiresAtTick));
        if (lifetimeTicks > 0) {
            PersistentBloodDecisionStore.store(
                    persistedPlayerData(player),
                    spellId,
                    decision,
                    lifetimeTicks
            );
        }
    }

    BloodCastDecision removeBloodDecision(Player player, ResourceLocation spellId) {
        Map<ResourceLocation, TimedBloodDecision> decisions = bloodDecisions.get(player.getUUID());
        TimedBloodDecision removed = null;
        if (decisions != null) {
            removed = decisions.remove(spellId);
            if (decisions.isEmpty()) {
                bloodDecisions.remove(player.getUUID(), decisions);
            }
        }
        CompoundTag playerData = existingPersistedPlayerData(player);
        BloodCastDecision persisted = playerData == null
                ? null
                : PersistentBloodDecisionStore.remove(playerData, spellId);
        return removed == null ? persisted : removed.decision();
    }

    void queueHolyHeal(LivingEntity entity, float amount, int serverTick) {
        if (amount > 0f && Float.isFinite(amount)) {
            pendingHeals.put(entity.getUUID(), new PendingHeal(amount, serverTick));
        }
    }

    boolean consumeMatchingHolyHeal(LivingEntity entity, float amount, int serverTick) {
        PendingHeal pending = pendingHeals.get(entity.getUUID());
        if (pending == null || pending.serverTick() != serverTick) {
            return false;
        }
        boolean matches = Float.isFinite(amount)
                && Math.abs(pending.amount() - amount) <= HEAL_AMOUNT_EPSILON;
        return matches && pendingHeals.remove(entity.getUUID(), pending);
    }

    void clearTransientPlayer(Player player) {
        clearTransientPlayer(player.getUUID());
    }

    void clearTransientPlayer(UUID playerId) {
        bloodDecisions.remove(playerId);
        pendingHeals.remove(playerId);
    }

    void clearPersistentPlayer(Player player) {
        CompoundTag persisted = existingPersistedPlayerData(player);
        if (persisted != null) {
            PersistentBloodDecisionStore.clear(persisted);
        }
    }

    void purgeExpired(int serverTick) {
        bloodDecisions.entrySet().removeIf(playerEntry -> {
            playerEntry.getValue().entrySet().removeIf(decisionEntry ->
                    decisionEntry.getValue().expiresAtTick() < serverTick);
            return playerEntry.getValue().isEmpty();
        });
        pendingHeals.entrySet().removeIf(entry -> entry.getValue().serverTick() != serverTick);
    }

    void tickPersistentDecisions(Player player) {
        CompoundTag persisted = existingPersistedPlayerData(player);
        if (persisted != null) {
            PersistentBloodDecisionStore.tick(persisted);
        }
    }

    void clearTransientAll() {
        bloodDecisions.clear();
        pendingHeals.clear();
    }

    private static CompoundTag persistedPlayerData(Player player) {
        CompoundTag entityData = player.getPersistentData();
        if (!entityData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            entityData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return entityData.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static CompoundTag existingPersistedPlayerData(Player player) {
        CompoundTag entityData = player.getPersistentData();
        return entityData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)
                ? entityData.getCompound(Player.PERSISTED_NBT_TAG)
                : null;
    }

    record BloodCastDecision(boolean spentBlood, int bloodCost, double cooldownMultiplier) {
    }

    private record TimedBloodDecision(BloodCastDecision decision, long expiresAtTick) {
    }

    private record PendingHeal(float amount, int serverTick) {
    }
}
