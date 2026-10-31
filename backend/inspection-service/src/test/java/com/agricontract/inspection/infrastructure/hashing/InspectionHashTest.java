package com.agricontract.inspection.infrastructure.hashing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.inspection.domain.exception.InspectionCommitmentMismatchException;
import com.agricontract.inspection.domain.model.InspectionSettlementResultV1;
import com.agricontract.inspection.domain.service.InspectionHashes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class InspectionHashTest {
    final InspectionHashInput input = new InspectionHashInput();
    final JcsCanonicalizer jcs = new JcsCanonicalizer();
    final InspectionHashes hashes = new InspectionHashes(jcs);
    static final String RESULT =
            """
{"resultSchemaVersion":"1.0","measuredQuantityKg":10,"acceptedQuantityKg":9,
 "measurementStatus":"COMPLETE","actualQualityMetrics":{"commodity":"COFFEE","type":"ROBUSTA",
 "moisturePercent":12,"foreignMatterPercent":1,"blackBrokenBeansPercent":2},
 "determinedAt":"2026-10-26T01:00:00.000Z"}
""";
    static final String REPORT =
            """
{"reportId":"11111111-1111-4111-8111-111111111111","contractId":"22222222-2222-4222-8222-222222222222",
 "milestoneId":"33333333-3333-4333-8333-333333333333","tier":"LEVEL_1_5",
 "reportFileHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
 "resultHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
 "sourceTimestamp":"2026-10-26T01:00:00.000Z","actorOrSourceIdentity":"44444444-4444-4444-8444-444444444444"}
""";

    ObjectNode tree(String value) throws Exception {
        return (ObjectNode) new ObjectMapper().readTree(value);
    }

    @Test
    void rfcNumberAndRecursiveOrderingVector() {
        // RFC 8785 section 3.2.2 and Appendix B.
        assertThat(
                        new String(
                                jcs.canonicalizeJson(
                                        "{\"z\":[1.0,1e0,-0.0,333333333.33333329,1E30,4.50,2e-3,1e-27],\"a\":{\"z\":0,\"a\":true}}"),
                                StandardCharsets.UTF_8))
                .isEqualTo(
                        "{\"a\":{\"a\":true,\"z\":0},\"z\":[1,1,0,333333333.3333333,1e+30,4.5,0.002,1e-27]}");
    }

    @Test
    void deterministicBytesAndEquivalentRepresentations() {
        var result = input.result(RESULT);
        String canonical = new String(jcs.canonicalize(result), StandardCharsets.UTF_8);
        assertThat(canonical)
                .isEqualTo(
                        "{\"acceptedQuantityKg\":9,\"actualQualityMetrics\":{\"blackBrokenBeansPercent\":2,\"commodity\":\"COFFEE\",\"foreignMatterPercent\":1,\"moisturePercent\":12,\"type\":\"ROBUSTA\"},\"determinedAt\":\"2026-10-26T01:00:00.000Z\",\"measuredQuantityKg\":10,\"measurementStatus\":\"COMPLETE\",\"resultSchemaVersion\":\"1.0\"}");
        for (int i = 0; i < 20; i++)
            assertThat(hashes.calculateResultHash(input.result(canonical)))
                    .isEqualTo(hashes.calculateResultHash(result));
        assertThat(
                        hashes.calculateResultHash(
                                input.result(RESULT.replace(":10", ":1e1").replace(":9", ":9.00"))))
                .isEqualTo(hashes.calculateResultHash(result));
        assertThat(hashes.calculateResultHash(result)).matches("[a-f0-9]{64}");
        // Independently computed with Python hashlib over the literal canonical fixture above.
        assertThat(hashes.calculateResultHash(result))
                .isEqualTo("aecf2098035578540000af76157f39b8a9c93fdab0d0a08cb74e0a0805a7b552");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "resultSchemaVersion",
                "measuredQuantityKg",
                "acceptedQuantityKg",
                "measurementStatus",
                "actualQualityMetrics",
                "determinedAt"
            })
    void requiredFieldsRejectAbsentAndNull(String field) throws Exception {
        var json = tree(RESULT);
        json.remove(field);
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        json.putNull(field);
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "qualityDisposition",
                "scoring",
                "reviewerOpinion",
                "certificateDerivedResult",
                "discount",
                "penalty",
                "entitlement",
                "settlementAmount"
            })
    void subjectiveFieldsRejectedAtBothLevels(String field) throws Exception {
        var json = tree(RESULT);
        json.put(field, "value");
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        json.remove(field);
        ((ObjectNode) json.get("actualQualityMetrics")).put(field, "value");
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void strictInputRejectsCoercionDuplicatesTrailingAndInvalidValues() {
        for (String bad :
                List.of(
                        RESULT + " {}",
                        RESULT.replace("\"1.0\"", "1.0"),
                        RESULT.replace(":10", ":\"10\""),
                        RESULT.replace(":10", ":true"),
                        RESULT.replace(":10", ":NaN"),
                        RESULT.replace(":10", ":Infinity"),
                        RESULT.replace(":10", ":-1"),
                        RESULT.replace(":9", ":11"),
                        RESULT.replace("COMPLETE", "UNKNOWN"),
                        RESULT.replace("ROBUSTA", "OTHER"),
                        RESULT.replace("COFFEE", "RICE"),
                        RESULT.replace("COFFEE", "RUBBER"),
                        RESULT.replace("COFFEE", "CASHEW"),
                        RESULT.replace(":12", ":101"),
                        RESULT.replace("2026-10-26T01:00:00.000Z", "invalid"),
                        RESULT.replace(
                                "\"measuredQuantityKg\":10",
                                "\"measuredQuantityKg\":10,\"measuredQuantityKg\":10")))
            assertThatThrownBy(() -> input.result(bad))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reasonsPreservePresenceAndOrder() throws Exception {
        var json = tree(RESULT);
        json.put("measurementStatus", "INCONCLUSIVE");
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        json.putArray("inconclusiveReasons");
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        json.putArray("inconclusiveReasons").add(" ");
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        json.putNull("inconclusiveReasons");
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        json.put("measurementStatus", "COMPLETE");
        json.putArray("inconclusiveReasons").add("a").add("b");
        String first = hashes.calculateResultHash(input.result(json.toString()));
        json.putArray("inconclusiveReasons").add("b").add("a");
        assertThat(hashes.calculateResultHash(input.result(json.toString()))).isNotEqualTo(first);
        assertThatThrownBy(() -> input.result(RESULT.replace(".000Z", "+00:00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "measuredQuantityKg",
                "acceptedQuantityKg",
                "measurementStatus",
                "type",
                "moisturePercent",
                "foreignMatterPercent",
                "blackBrokenBeansPercent",
                "inconclusiveReasons",
                "determinedAt"
            })
    void everyResultFieldTamperingIsRejected(String field) throws Exception {
        var json = tree(RESULT);
        json.putArray("inconclusiveReasons").add("measurement note");
        String expected = hashes.calculateResultHash(input.result(json.toString()));
        switch (field) {
            case "measurementStatus" -> json.put(field, "INCONCLUSIVE");
            case "type" -> ((ObjectNode) json.get("actualQualityMetrics")).put(field, "ARABICA");
            case "determinedAt" -> json.put(field, "2026-10-26T01:00:01.000Z");
            case "inconclusiveReasons" -> json.putArray(field).add("changed");
            case "measuredQuantityKg" -> json.put(field, 11);
            case "acceptedQuantityKg" -> json.put(field, 8);
            default -> ((ObjectNode) json.get("actualQualityMetrics")).put(field, 3);
        }
        assertThatThrownBy(() -> hashes.verifyResultHash(input.result(json.toString()), expected))
                .isInstanceOf(InspectionCommitmentMismatchException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "reportId",
                "contractId",
                "milestoneId",
                "tier",
                "reportFileHash",
                "resultHash",
                "sourceTimestamp",
                "actorOrSourceIdentity"
            })
    void everyReportFieldTamperingIsRejected(String field) throws Exception {
        var json = tree(REPORT);
        String expected = hashes.calculateReportHash(input.report(REPORT));
        json.put(
                field,
                switch (field) {
                    case "tier" -> "LEVEL_2";
                    case "reportFileHash", "resultHash" -> "c".repeat(64);
                    case "sourceTimestamp" -> "2026-10-26T01:00:01.000Z";
                    default -> "55555555-5555-4555-8555-555555555555";
                });
        assertThatThrownBy(() -> hashes.verifyReportHash(input.report(json.toString()), expected))
                .isInstanceOf(InspectionCommitmentMismatchException.class);
    }

    @Test
    void verificationAcceptsExactCommitmentAndRejectsMismatchedOrMalformedHashes() {
        var result = input.result(RESULT);
        var report = input.report(REPORT);
        hashes.verifyResultHash(result, hashes.calculateResultHash(result));
        hashes.verifyReportHash(report, hashes.calculateReportHash(report));
        for (String bad : List.of("x", "A".repeat(64))) {
            assertThatThrownBy(() -> hashes.verifyResultHash(result, bad))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> hashes.verifyReportHash(report, bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void unicodeOrderingEscapingAndInvalidSurrogates() {
        // RFC8785 section 3.2.3 property-sorting sample, sorted by UTF-16 code units.
        String source = "{\"€\":1,\"\\r\":2,\"דּ\":3,\"1\":4,\"😀\":5,\"\\u0080\":6,\"ö\":7}";
        assertThat(new String(jcs.canonicalizeJson(source), StandardCharsets.UTF_8))
                .isEqualTo("{\"\\r\":2,\"1\":4,\"\u0080\":6,\"ö\":7,\"€\":1,\"😀\":5,\"דּ\":3}");
        assertThat(jcs.canonicalizeJson("{\"x\":\"\\u0061\"}"))
                .isEqualTo(jcs.canonicalizeJson("{\"x\":\"a\"}"));
        assertThat(jcs.canonicalizeJson("{\"x\":\"é\"}"))
                .isNotEqualTo(jcs.canonicalizeJson("{\"x\":\"é\"}"));
        for (String bad :
                List.of("{\"x\":\"\\ud800\"}", "{\"\\udc00\":1}", "{\"x\":1,\"x\":2}", "{} {}"))
            assertThatThrownBy(() -> jcs.canonicalizeJson(bad))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "commodity",
                "type",
                "moisturePercent",
                "foreignMatterPercent",
                "blackBrokenBeansPercent"
            })
    void metricsRequireEveryNonNullField(String field) throws Exception {
        var json = tree(RESULT);
        var metrics = (ObjectNode) json.get("actualQualityMetrics");
        metrics.remove(field);
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        metrics.putNull(field);
        assertThatThrownBy(() -> input.result(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "reportId",
                "contractId",
                "milestoneId",
                "tier",
                "reportFileHash",
                "resultHash",
                "sourceTimestamp",
                "actorOrSourceIdentity"
            })
    void reportRequiresExactNonNullStringFields(String field) throws Exception {
        var json = tree(REPORT);
        json.remove(field);
        assertThatThrownBy(() -> input.report(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        json.putNull(field);
        assertThatThrownBy(() -> input.report(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        json.put(field, 123);
        assertThatThrownBy(() -> input.report(json.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> input.report(REPORT.replace("aaaaaaaa", "AAAAAAAA")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timestampAndReportProjectionArePreservedExactly() throws Exception {
        var report = input.report(REPORT);
        var projection = tree(new String(jcs.canonicalize(report), StandardCharsets.UTF_8));
        assertThat(projection.size()).isEqualTo(8);
        assertThat(projection).isEqualTo(tree(REPORT));
        // Independent Python hashlib fixture over the exact sorted string-only projection.
        assertThat(hashes.calculateReportHash(report))
                .isEqualTo("0504d370660249364690c677c6f8131eea29ca9864349f58eb8f828b082158ba");
    }

    @Test
    void additionalInvalidValuesAndMutableCallerListCannotBypassBoundary() throws Exception {
        for (String bad :
                List.of(
                        RESULT.replace("\"1.0\"", "\"2.0\""),
                        RESULT.replace(":12", ":-0.1"),
                        RESULT.replace("\"ROBUSTA\"", "123"),
                        RESULT.replace(
                                "\"foreignMatterPercent\":1", "\"foreignMatterPercent\":\"1\"")))
            assertThatThrownBy(() -> input.result(bad))
                    .isInstanceOf(IllegalArgumentException.class);
        var extra = tree(REPORT);
        extra.put("confirmedAt", "2026-10-26T01:00:00Z");
        assertThatThrownBy(() -> input.report(extra.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        var r = input.result(RESULT);
        var reasons = new ArrayList<>(List.of("original"));
        var copy =
                new InspectionSettlementResultV1(
                        r.resultSchemaVersion(),
                        r.measuredQuantityKg(),
                        r.acceptedQuantityKg(),
                        r.measurementStatus(),
                        r.actualQualityMetrics(),
                        reasons,
                        r.determinedAt());
        String hash = hashes.calculateResultHash(copy);
        reasons.set(0, "changed");
        assertThat(hashes.calculateResultHash(copy)).isEqualTo(hash);
        assertThatThrownBy(() -> copy.inconclusiveReasons().add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
