package com.vampirespells.addon.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

final class DamageStateTracker {

    private final ThreadLocal<Deque<DamageSnapshot>> snapshots = ThreadLocal.withInitial(ArrayDeque::new);

    void capture(LivingDamageEvent.Pre event) {
        snapshots.get().push(new DamageSnapshot(event.getEntity(), event.getSource(), event.getEntity().getHealth()));
    }

    float finish(LivingDamageEvent.Post event) {
        Deque<DamageSnapshot> stack = snapshots.get();
        DamageSnapshot match = removeMatching(stack, event.getEntity(), event.getSource());
        if (stack.isEmpty()) {
            snapshots.remove();
        }

        float postDamage = finitePositive(event.getNewDamage());
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
