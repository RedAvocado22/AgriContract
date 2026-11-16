package com.agricontract.reputation.application;

import com.agricontract.reputation.application.port.LockEventPublisher;
import com.agricontract.reputation.application.port.LockStateStore;
import com.agricontract.reputation.application.port.TransactionRunner;
import com.agricontract.reputation.application.usecase.LockEventLifecycle;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LockEventLifecycleTest {
    final UUID user = UUID.randomUUID(), decision = UUID.randomUUID(), source = UUID.randomUUID();
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    final LockStateStore states = mock(LockStateStore.class);
    final LockEventPublisher events = mock(LockEventPublisher.class);
    final TransactionRunner runner = new DirectTransactionRunner();
    final LockEventLifecycle lifecycle = new LockEventLifecycle(runner, states, events);

    @Test void mutexIsAcquiredBeforeLedgerWriteAndEventFollowsProjection() {
        var append = mock(Runnable.class);
        when(states.acquire(user)).thenReturn(new LockStateStore.State(0, null));
        when(states.now()).thenReturn(now);
        when(states.requireLock(user, decision)).thenReturn(source);
        when(states.effectiveUntil(user, now)).thenReturn(now.plusSeconds(60));
        assertThat(lifecycle.lockCreated(user, decision, "SHORTFALL", "workflow", append)).isTrue();
        var order = inOrder(states, append, events);
        order.verify(states).acquire(user);
        order.verify(states).alreadyPublished("LOCK", decision, user, "SHORTFALL");
        order.verify(append).run();
        order.verify(states).requireLock(user, decision);
        order.verify(states).now();
        order.verify(states).effectiveUntil(user, now);
        order.verify(states).save(user, 1, now.plusSeconds(60), now);
        order.verify(events).publish(user, true, now.plusSeconds(60), "SHORTFALL", 1, now, "workflow", source);
        order.verify(states).remember("LOCK", decision, user, "SHORTFALL");
    }

    @Test void clearedOrNotYetDueStateDoesNotAllocateRevision() {
        when(states.now()).thenReturn(now);
        for (Instant until : new Instant[] {null, now.plusSeconds(1)}) {
            when(states.acquire(user)).thenReturn(new LockStateStore.State(7, until));
            assertThat(lifecycle.expire(user, "scan")).isFalse();
        }
        verifyNoInteractions(events);
        verify(states, never()).effectiveUntil(any(), any());
    }

    @Test void dueCandidateWithAnotherActiveEntryCannotClearLock() {
        when(states.acquire(user)).thenReturn(new LockStateStore.State(3, now));
        when(states.now()).thenReturn(now);
        when(states.effectiveUntil(user, now)).thenReturn(now.plusSeconds(10));
        assertThat(lifecycle.expire(user, "scan")).isFalse();
        verifyNoInteractions(events);
        verify(states, never()).save(any(), anyLong(), any(), any());
    }

    @Test void revisionOverflowFailsWithoutPersistingOrPublishing() {
        when(states.acquire(user)).thenReturn(new LockStateStore.State(Long.MAX_VALUE, now));
        when(states.now()).thenReturn(now);
        assertThatThrownBy(() -> lifecycle.expire(user, "scan")).isInstanceOf(ArithmeticException.class);
        verifyNoInteractions(events);
        verify(states, never()).save(any(), anyLong(), any(), any());
    }

    @Test void invalidWireReasonAndMissingWorkflowFailBeforeTransactionWork() {
        assertThatThrownBy(() -> lifecycle.lockCreated(user, decision, "CONTRACT_BREACH", "workflow", () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.expire(user, " ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(states, events);
    }
}
