package com.agricontract.product.infrastructure.scheduling;

import com.agricontract.product.application.usecase.ReapOrphanReservationsUseCase;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrphanReservationReaperJobTest {

    private final ReapOrphanReservationsUseCase useCase = mock(ReapOrphanReservationsUseCase.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void reapsReservationsCreatedBeforeTheGracePeriod() {
        new OrphanReservationReaperJob(useCase, clock, 30).reapOrphanReservations();

        verify(useCase).execute(Instant.parse("2026-10-27T09:30:00Z"));
    }

    @Test
    void zeroGrace_isRejectedAtStartup() {
        assertThatThrownBy(() -> new OrphanReservationReaperJob(useCase, clock, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
