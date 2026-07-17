package com.vampirespells.addon.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CastStateTracker {

    private static final float HEAL_AMOUNT_EPSILON = 0.0001f;

    private final Map<UUID, PendingHeal> pendingHeals = new ConcurrentHashMap<>();

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
        pendingHeals.remove(player.getUUID());
    }

    void purgeExpired(int serverTick) {
        pendingHeals.entrySet().removeIf(entry -> entry.getValue().serverTick() != serverTick);
    }

    void clearTransientAll() {
        pendingHeals.clear();
    }

    private record PendingHeal(float amount, int serverTick) {
    }
}
