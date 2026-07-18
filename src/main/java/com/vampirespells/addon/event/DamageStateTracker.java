package com.vampirespells.addon.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

final class DamageStateTracker {

    private final ThreadLocal<Deque<DamageSnapshot>> snapshots = ThreadLocal.withInitial(ArrayDeque::new);

    void capture(LivingEntity entity, DamageSource source) {
        snapshots.get().push(new DamageSnapshot(entity, source, entity.getHealth()));
    }

    float finish(LivingEntity entity, DamageSource source, float deliveredDamage) {
        Deque<DamageSnapshot> stack = snapshots.get();
        DamageSnapshot match = removeMatching(stack, entity, source);
        if (stack.isEmpty()) {
            snapshots.remove();
        }

        float postDamage = finitePositive(deliveredDamage);
        if (match == null) {
            return postDamage;
        }
        return Math.min(postDamage, finitePositive(match.healthBefore()));
    }

    void clear() {
        snapshots.remove();
    }

    private static DamageSnapshot removeMatching(
            Deque<DamageSnapshot> stack,
            LivingEntity entity,
            DamageSource source
    ) {
        Iterator<DamageSnapshot> iterator = stack.iterator();
        while (iterator.hasNext()) {
            DamageSnapshot candidate = iterator.next();
            if (candidate.entity() == entity && candidate.source() == source) {
                iterator.remove();
                return candidate;
            }
        }
        return null;
    }

    private static float finitePositive(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    private record DamageSnapshot(LivingEntity entity, DamageSource source, float healthBefore) {
    }
}
