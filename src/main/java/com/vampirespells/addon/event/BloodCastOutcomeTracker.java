package com.vampirespells.addon.event;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.UUID;

final class BloodCastOutcomeTracker {

    private static final int MAX_PENDING_OUTCOMES = 32;

    private final ThreadLocal<Deque<OutcomeEntry>> pending =
            ThreadLocal.withInitial(ArrayDeque::new);

    void record(UUID playerId, ResourceLocation spellId, boolean denied) {
        Deque<OutcomeEntry> outcomes = pending.get();
        while (outcomes.size() >= MAX_PENDING_OUTCOMES) {
            outcomes.removeFirst();
        }
        outcomes.addLast(new OutcomeEntry(playerId, spellId, denied));
    }

    boolean consumeDenied(UUID playerId, ResourceLocation spellId) {
        Deque<OutcomeEntry> outcomes = pending.get();
        Iterator<OutcomeEntry> iterator = outcomes.descendingIterator();
        while (iterator.hasNext()) {
            OutcomeEntry outcome = iterator.next();
            if (outcome.playerId().equals(playerId) && outcome.spellId().equals(spellId)) {
                iterator.remove();
                if (outcomes.isEmpty()) {
                    pending.remove();
                }
                return outcome.denied();
            }
        }
        if (outcomes.isEmpty()) {
            pending.remove();
        }
        return false;
    }

    private record OutcomeEntry(UUID playerId, ResourceLocation spellId, boolean denied) {
    }
}
