package com.agricontract.inspection.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.inspection.domain.exception.InspectionCommitmentMismatchException;
import com.agricontract.inspection.domain.model.CoffeeActualMetrics;
import com.agricontract.inspection.domain.model.CoffeeType;
import com.agricontract.inspection.domain.model.InspectionSettlementResultV1;
import com.agricontract.inspection.domain.model.InspectionTier;
import com.agricontract.inspection.domain.model.MeasurementStatus;
import com.agricontract.inspection.domain.model.ReportCommitment;
import com.agricontract.inspection.infrastructure.hashing.JcsCanonicalizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

class InspectionHashesTest {
    private static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TIME = "2026-10-26T01:00:00.000Z";
    private final InspectionHashes hashes = new InspectionHashes(new JcsCanonicalizer());

    private CoffeeActualMetrics metrics(String moisture, String foreign, String black) {
        return new CoffeeActualMetrics(
                "COFFEE",
                CoffeeType.ROBUSTA,
                new BigDecimal(moisture),
                new BigDecimal(foreign),
                new BigDecimal(black));
    }

    private InspectionSettlementResultV1 result(
            String measured, String accepted, CoffeeActualMetrics metrics, String time) {
        return new InspectionSettlementResultV1(
                "1.0",
                new BigDecimal(measured),
                new BigDecimal(accepted),
                MeasurementStatus.COMPLETE,
                metrics,
                null,
                time);
    }

    private ReportCommitment report(
            String report, String contract, String milestone, String actor, String time) {
        return new ReportCommitment(
                report,
                contract,
                milestone,
                InspectionTier.LEVEL_1_5,
                "a".repeat(64),
                "b".repeat(64),
                time,
                actor);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2026-10-26T01:00:00Z",
                "2026-10-26T01:00:00.00Z",
                "2026-10-26T01:00:00.0000Z",
                "2026-10-26T08:00:00.000+07:00",
                "2026-10-26T01:00:00.000+00:00",
                "2026-10-26t01:00:00.000z",
                "2026-10-26T01:00:60.000Z",
                "2016-12-31T23:59:60.000Z",
                "2026-02-30T01:00:00.000Z",
                "2026-10-26T24:00:00.000Z"
            })
    void noncanonicalOrInvalidTimestampsAreRejectedBeforeHashing(String time) {
        assertThatThrownBy(
                        () ->
                                hashes.calculateResultHash(
                                        result("10", "9", metrics("12", "1", "2"), time)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hashes.calculateReportHash(report(ID, ID, ID, ID, time)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalTimestampAndLowercaseIdentitiesAreAccepted() {
        assertThat(hashes.calculateResultHash(result("10", "9", metrics("12", "1", "2"), TIME)))
                .matches("[a-f0-9]{64}");
        assertThat(hashes.calculateReportHash(report(ID, ID, ID, ID, TIME)))
                .matches("[a-f0-9]{64}");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void uppercaseUuidCannotProduceAlternativeCommitmentForSameIdentity(int field) {
        String[] ids = {ID, ID, ID, ID};
        ids[field] = ID.toUpperCase(Locale.ROOT);
        assertThatThrownBy(
                        () ->
                                hashes.calculateReportHash(
                                        report(ids[0], ids[1], ids[2], ids[3], TIME)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.001", "12.340", "1000000", "1000000.000"})
    void quantityBoundsAcceptMeaningfulScaleAndTrailingZeros(String quantity) {
        assertThat(
                        hashes.calculateResultHash(
                                result(quantity, quantity, metrics("12", "1", "2"), TIME)))
                .matches("[a-f0-9]{64}");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"-0.001", "1000000.001", "0.0001", "9007199254740992", "9007199254740993"})
    void bothQuantitiesRejectOutOfDomainValues(String quantity) {
        assertThatThrownBy(() -> result(quantity, "0", metrics("12", "1", "2"), TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> result("1000000", quantity, metrics("12", "1", "2"), TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.01", "12.34", "12.340", "100", "100.00"})
    void allPercentagesAcceptMeaningfulScaleAndTrailingZeros(String percent) {
        assertThat(
                        hashes.calculateResultHash(
                                result("10", "9", metrics(percent, percent, percent), TIME)))
                .matches("[a-f0-9]{64}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "12.345", "100.01", "0.001"})
    void everyPercentageRejectsOutOfDomainValues(String percent) {
        assertThatThrownBy(() -> metrics(percent, "1", "2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics("12", percent, "2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics("12", "1", percent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptedQuantityCannotExceedMeasuredQuantity() {
        assertThatThrownBy(() -> result("0.001", "0.002", metrics("12", "1", "2"), TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distinctAcceptedNumbersDoNotCollapseThroughJcsBinary64() {
        var metrics = metrics("12.34", "1", "2");
        assertThat(hashes.calculateResultHash(result("999999.998", "0", metrics, TIME)))
                .isNotEqualTo(hashes.calculateResultHash(result("999999.999", "0", metrics, TIME)));
        assertThat(hashes.calculateResultHash(result("1000000", "999999.998", metrics, TIME)))
                .isNotEqualTo(
                        hashes.calculateResultHash(result("1000000", "999999.999", metrics, TIME)));
        String baseline =
                hashes.calculateResultHash(
                        result("10", "9", metrics("12.34", "12.34", "12.34"), TIME));
        for (var changed :
                List.of(
                        metrics("12.35", "12.34", "12.34"),
                        metrics("12.34", "12.35", "12.34"),
                        metrics("12.34", "12.34", "12.35"))) {
            assertThat(hashes.calculateResultHash(result("10", "9", changed, TIME)))
                    .isNotEqualTo(baseline);
        }
        assertThat(
                        hashes.calculateResultHash(
                                result("12.340", "9.000", metrics("12.340", "1.00", "2.00"), TIME)))
                .isEqualTo(hashes.calculateResultHash(result("12.34", "9", metrics, TIME)));
    }

    @Test
    void validDigestMismatchIsDistinctFromMalformedExpectedHash() {
        var result = result("10", "9", metrics("12", "1", "2"), TIME);
        var report = report(ID, ID, ID, ID, TIME);
        assertThatThrownBy(() -> hashes.verifyResultHash(result, "0".repeat(64)))
                .isExactlyInstanceOf(InspectionCommitmentMismatchException.class);
        assertThatThrownBy(() -> hashes.verifyReportHash(report, "0".repeat(64)))
                .isExactlyInstanceOf(InspectionCommitmentMismatchException.class);
        for (String malformed : new String[] {null, "", "abc", "A".repeat(64)}) {
            assertThatThrownBy(() -> hashes.verifyResultHash(result, malformed))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> hashes.verifyReportHash(report, malformed))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }
}
