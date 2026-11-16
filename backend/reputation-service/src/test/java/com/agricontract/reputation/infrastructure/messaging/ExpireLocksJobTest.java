package com.agricontract.reputation.infrastructure.messaging;

import com.agricontract.reputation.application.port.LockStateStore;
import com.agricontract.reputation.application.usecase.LockEventLifecycle;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpireLocksJobTest {
    @Test void failedUserDoesNotPreventProcessingOtherUsersAndNextPassRetries() {
        var states = mock(LockStateStore.class);
        var lifecycle = mock(LockEventLifecycle.class);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var a = new LockStateStore.DueLock(now, first);
        var b = new LockStateStore.DueLock(now, second);
        when(states.now()).thenReturn(now);
        when(states.dueUsers(now, null, 10)).thenReturn(List.of(a, b));
        when(states.dueUsers(now, b, 10)).thenReturn(List.of());
        when(lifecycle.expire(eq(first), anyString())).thenThrow(new IllegalStateException("test rollback")).thenReturn(true);
        var job = new ExpireLocksJob(states, lifecycle, 10);
        job.run(); job.run();
        verify(lifecycle, times(2)).expire(eq(first), anyString());
        verify(lifecycle, times(2)).expire(eq(second), anyString());
    }

    @Test void rejectsInvalidBatchSize() {
        for (int size : new int[] {0, -1, 1001})
            assertThatThrownBy(() -> new ExpireLocksJob(mock(LockStateStore.class), mock(LockEventLifecycle.class), size))
                    .isInstanceOf(IllegalArgumentException.class);
    }
}
