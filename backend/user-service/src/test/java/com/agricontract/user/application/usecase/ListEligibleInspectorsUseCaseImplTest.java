package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EligibleInspectorResponse;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListEligibleInspectorsUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");
    private final UserProfileRepository users = mock(UserProfileRepository.class);
    private final ListEligibleInspectorsUseCase useCase =
            new ListEligibleInspectorsUseCaseImpl(users, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void executeUsesCurrentClockAndMapsOnlyUserIdsInRepositoryOrder() {
        when(users.findEligibleInspectorIds(NOW)).thenReturn(List.of("inspector-2", "inspector-1"));

        assertThat(useCase.execute()).containsExactly(
                new EligibleInspectorResponse("inspector-2"), new EligibleInspectorResponse("inspector-1"));
        verify(users).findEligibleInspectorIds(NOW);
    }

    @Test
    void executeReturnsEmptyListForEmptyPool() {
        when(users.findEligibleInspectorIds(NOW)).thenReturn(List.of());

        assertThat(useCase.execute()).isEmpty();
        verify(users).findEligibleInspectorIds(NOW);
    }
}
