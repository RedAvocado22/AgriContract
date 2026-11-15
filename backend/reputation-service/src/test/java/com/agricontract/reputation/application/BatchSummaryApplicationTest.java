package com.agricontract.reputation.application;

import com.agricontract.reputation.application.dto.BatchSummaryQuery;
import com.agricontract.reputation.application.dto.PublicSummaryQuery;
import com.agricontract.reputation.application.usecase.GetPublicReputationSummaries;
import com.agricontract.reputation.application.usecase.GetPublicReputationSummary;
import com.agricontract.reputation.domain.model.ReputationReadProjection;
import com.agricontract.reputation.domain.repository.ReputationReadRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BatchSummaryApplicationTest {
    @Test
    void bothUseCasesPropagateRepositoryFailuresWithoutSpring() {
        var repository = mock(ReputationReadRepository.class);
        var clock = Clock.systemUTC();
        var failure = new IllegalStateException("repository failed");
        when(repository.publicSummaries(any(), any()))
                .thenThrow(failure);
        var ids = List.of(UUID.randomUUID());
        assertThatThrownBy(() -> new GetPublicReputationSummary(repository, clock, new DirectTransactionRunner())
                .execute(new PublicSummaryQuery(ids.getFirst()))).isSameAs(failure);
        assertThatThrownBy(() -> new GetPublicReputationSummaries(repository, clock, new DirectTransactionRunner())
                .execute(new BatchSummaryQuery(ids))).isSameAs(failure);
    }

    @Test
    void batchCallsOneRepositoryOperationAndCapturesClockOnce() {
        var repository = mock(ReputationReadRepository.class);
        var clock = mock(Clock.class);
        var now = Instant.parse("2026-11-05T00:00:00Z");
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(clock.instant()).thenReturn(now);
        when(repository.publicSummaries(ids, now)).thenReturn(List.of(
                new ReputationReadProjection(ids.get(0), BigDecimal.valueOf(67), true),
                new ReputationReadProjection(ids.get(1), null, false)));
        var result = new GetPublicReputationSummaries(repository, clock, new DirectTransactionRunner()).execute(new BatchSummaryQuery(ids));
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getFirst().score()).isEqualByComparingTo("67");
        assertThat(result.items().get(1).score()).isNull();
        assertThatThrownBy(() -> result.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).publicSummaries(ids, now);
        verify(clock).instant();
        verifyNoMoreInteractions(repository, clock);
    }

    @Test
    void publicSummaryUsesSameProjectionAndTimeForHistory() {
        var repository = mock(ReputationReadRepository.class);
        var clock = mock(Clock.class);
        var now = Instant.parse("2026-11-05T00:00:00Z");
        var id = UUID.randomUUID();
        when(clock.instant()).thenReturn(now);
        when(repository.publicSummaries(List.of(id), now)).thenReturn(List.of(
                new ReputationReadProjection(id, BigDecimal.valueOf(80), true)));
        when(repository.lockHistory(id, now)).thenReturn(List.of());
        var result = new GetPublicReputationSummary(repository, clock, new DirectTransactionRunner()).execute(new PublicSummaryQuery(id));
        assertThat(result.score()).isEqualByComparingTo("80");
        assertThat(result.locked()).isTrue();
        verify(repository).publicSummaries(List.of(id), now);
        verify(repository).lockHistory(id, now);
        verify(clock).instant();
        verifyNoMoreInteractions(repository, clock);
    }

    @Test
    void queryMakesDefensiveCopyAndRejectsDuplicates() {
        var ids = new ArrayList<>(List.of(UUID.randomUUID()));
        var query = new BatchSummaryQuery(ids);
        ids.clear();
        assertThat(query.userIds()).hasSize(1);
        assertThatThrownBy(() -> query.userIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> new BatchSummaryQuery(List.of(id, id))).isInstanceOf(IllegalArgumentException.class);
    }
}
