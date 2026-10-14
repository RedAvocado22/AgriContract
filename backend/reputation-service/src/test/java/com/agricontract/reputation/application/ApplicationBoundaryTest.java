package com.agricontract.reputation.application;

import com.agricontract.reputation.application.dto.*;
import com.agricontract.reputation.application.exception.ImmutableFactConflictException;
import com.agricontract.reputation.application.usecase.*;
import com.agricontract.reputation.domain.model.*;
import com.agricontract.reputation.domain.repository.*;
import com.agricontract.reputation.infrastructure.contract.FrozenSchemas;
import com.agricontract.reputation.infrastructure.web.PublicSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationBoundaryTest {
    final FactRepository facts = mock(FactRepository.class);
    final AppendReputationFact append = new AppendReputationFact(facts);
    final UUID contract = UUID.randomUUID(), buyer = UUID.randomUUID(), seller = UUID.randomUUID();
    final UUID event = UUID.randomUUID(), milestone = UUID.randomUUID();
    final Instant time = Instant.parse("2026-10-13T00:00:00.123456789Z");
    @Test void useCaseEntryPointsExposeOnlyImmutableApplicationDtos() {
        for (Class<?> useCase : List.of(AppendReputationFact.class, GetPublicReputationSummary.class)) {
            for (var method : useCase.getDeclaredMethods()) {
                if (!method.getName().equals("execute")) continue;
                assertThat(method.getReturnType().isRecord()).isTrue();
                assertThat(method.getReturnType().getPackageName()).isEqualTo("com.agricontract.reputation.application.dto");
                assertThat(method.getParameterTypes()).allSatisfy(type -> {
                    assertThat(type.isRecord()).isTrue();
                    assertThat(type.getPackageName()).isEqualTo("com.agricontract.reputation.application.dto");
                });
            }
        }
    }

    @Test void completionCommandMapsEveryFieldAndDomainNormalizesTime() {
        when(facts.append(any(CompletedContractFact.class))).thenReturn(FactRepository.AppendResult.INSERTED);
        var result = append.execute(new AppendCompletedContractCommand(contract, buyer, seller, time, event));
        var captured = ArgumentCaptor.forClass(CompletedContractFact.class);
        verify(facts).append(captured.capture());
        assertThat(captured.getValue()).isEqualTo(new CompletedContractFact(contract, buyer, seller, time, event));
        assertThat(captured.getValue().settledAt()).isEqualTo(Instant.parse("2026-10-13T00:00:00.123456Z"));
        assertThat(result).isEqualTo(new AppendFactResult(AppendFactResult.Status.INSERTED));
    }
    @Test void disputeCommandPreservesFlaggerResolutionAndDuplicateResult() {
        when(facts.append(any(DisputeOutcomeFact.class))).thenReturn(FactRepository.AppendResult.DUPLICATE);
        var result = append.execute(new AppendDisputeOutcomeCommand(milestone, contract, buyer, "SELLER", time, event));
        verify(facts).append(new DisputeOutcomeFact(milestone, contract, buyer, ResolutionFavors.SELLER, time, event));
        assertThat(result.status()).isEqualTo(AppendFactResult.Status.DUPLICATE);
    }
    @Test void invalidResolutionDoesNotReachPersistence() {
        assertThatThrownBy(() -> append.execute(new AppendDisputeOutcomeCommand(
                milestone, contract, buyer, "UNKNOWN", time, event))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(facts);
    }
    @Test void immutableConflictIsNotConvertedIntoSuccess() {
        when(facts.append(any(CompletedContractFact.class))).thenThrow(new ImmutableFactConflictException());
        assertThatThrownBy(() -> append.execute(new AppendCompletedContractCommand(contract, buyer, seller, time, event)))
                .isInstanceOf(ImmutableFactConflictException.class);
    }
    @Test void queryMapsDomainHistoryToImmutableResultAndExplicitHttpAllowlist() throws Exception {
        var reads = mock(ReputationReadRepository.class);
        var history = new ArrayList<>(List.of(new LockHistoryEntry(time, time.plusSeconds(86400), 1,
                ResolutionFavors.BUYER, LockHistoryEntry.Status.ACTIVE)));
        when(reads.lockHistory(buyer, time)).thenReturn(history);
        when(reads.countCompleted(buyer)).thenReturn(4L);
        var result = new GetPublicReputationSummary(reads, Clock.fixed(time, ZoneOffset.UTC))
                .execute(new PublicSummaryQuery(buyer));
        assertThat(result.userId()).isEqualTo(buyer);
        assertThat(result.score()).isEqualByComparingTo("80");
        assertThat(result.locked()).isTrue();
        assertThat(result.lockHistory()).containsExactly(
                new PublicSummaryResult.LockResult(time, time.plusSeconds(86400), 1, "BUYER", "ACTIVE"));
        history.clear();
        assertThat(result.lockHistory()).hasSize(1);
        assertThatThrownBy(() -> result.lockHistory().clear()).isInstanceOf(UnsupportedOperationException.class);
        var response = PublicSummaryResponse.from(result);
        assertThatThrownBy(() -> response.lockHistory().clear()).isInstanceOf(UnsupportedOperationException.class);
        var tree = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).valueToTree(response);
        assertThat(tree.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("userId", "score", "locked", "lockHistory");
        assertThat(tree.path("lockHistory").get(0).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("lockedAt", "lockedUntil", "lockDurationDays", "penalizedRole", "status");
        FrozenSchemas.assertResponse("ReputationPublicSummary", tree);
    }
}
