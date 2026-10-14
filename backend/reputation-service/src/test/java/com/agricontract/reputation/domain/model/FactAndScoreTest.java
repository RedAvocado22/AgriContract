package com.agricontract.reputation.domain.model;

import com.agricontract.reputation.application.usecase.GetPublicReputationSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class FactAndScoreTest {
    static final UUID ID = UUID.randomUUID();
    static final Instant TIME = Instant.parse("2026-10-13T00:00:00.123456789Z");
    @ParameterizedTest
    @CsvSource({"0,0,NULL", "5,0,100", "4,1,80", "1,7,13", "0,7,0", "2,1,67", "1,2,33"})
    void approvedBaseline(long completed, long locks, String score) {
        var actual = GetPublicReputationSummary.score(completed, locks);
        if (score.equals("NULL")) assertThat(actual).isNull();
        else assertThat(actual).isEqualByComparingTo(new BigDecimal(score)).hasScaleOf(0);
    }
    @Test void countsDoNotOverflowLongAddition() {
        assertThat(GetPublicReputationSummary.score(Long.MAX_VALUE, Long.MAX_VALUE)).isEqualByComparingTo("50");
    }
    @Test void invalidCountsFail() {
        assertThatThrownBy(() -> GetPublicReputationSummary.score(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GetPublicReputationSummary.score(0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void factsAreImmutableRecordsAndNormalizeToDatabasePrecision() {
        var completed = new CompletedContractFact(ID, ID, ID, TIME, ID);
        var dispute = new DisputeOutcomeFact(ID, ID, ID, ResolutionFavors.SELLER, TIME, ID);
        assertThat(CompletedContractFact.class.isRecord()).isTrue();
        assertThat(DisputeOutcomeFact.class.isRecord()).isTrue();
        assertThat(completed.settledAt()).isEqualTo(Instant.parse("2026-10-13T00:00:00.123456Z"));
        assertThat(dispute.resolvedAt()).isEqualTo(completed.settledAt());
    }
    static Stream<Runnable> missingFields() {
        return Stream.of(
            () -> new CompletedContractFact(null,ID,ID,TIME,ID),
            () -> new CompletedContractFact(ID,null,ID,TIME,ID),
            () -> new CompletedContractFact(ID,ID,null,TIME,ID),
            () -> new CompletedContractFact(ID,ID,ID,null,ID),
            () -> new CompletedContractFact(ID,ID,ID,TIME,null),
            () -> new DisputeOutcomeFact(null,ID,ID,ResolutionFavors.BUYER,TIME,ID),
            () -> new DisputeOutcomeFact(ID,null,ID,ResolutionFavors.BUYER,TIME,ID),
            () -> new DisputeOutcomeFact(ID,ID,null,ResolutionFavors.BUYER,TIME,ID),
            () -> new DisputeOutcomeFact(ID,ID,ID,null,TIME,ID),
            () -> new DisputeOutcomeFact(ID,ID,ID,ResolutionFavors.BUYER,null,ID),
            () -> new DisputeOutcomeFact(ID,ID,ID,ResolutionFavors.BUYER,TIME,null));
    }
    @ParameterizedTest @MethodSource("missingFields")
    void requiredFields(Runnable invalid) { assertThatThrownBy(invalid::run).isInstanceOf(NullPointerException.class); }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"1-1-1-1-1","not-uuid","", " 00000000-0000-0000-0000-000000000001"})
    void strictUuid(String value) { assertThatThrownBy(() -> FactValues.uuid(value)).isInstanceOf(IllegalArgumentException.class); }
}
