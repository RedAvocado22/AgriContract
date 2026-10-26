package com.agricontract.inspection.application.usecase;

import com.agricontract.inspection.application.port.InspectionCanonicalizer;
import com.agricontract.inspection.domain.model.InspectionSettlementResultV1;
import com.agricontract.inspection.domain.model.ReportCommitment;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** Pure integrity capability: does not authenticate callers or verify stored file bytes. */
@Service
public class InspectionHashes {
    private final InspectionCanonicalizer canonicalizer;
    public InspectionHashes(InspectionCanonicalizer canonicalizer) { this.canonicalizer = canonicalizer; }

    public String calculateResultHash(InspectionSettlementResultV1 result) { return sha256(canonicalizer.canonicalize(result)); }
    public String calculateReportHash(ReportCommitment report) { return sha256(canonicalizer.canonicalize(report)); }
    public void verifyResultHash(InspectionSettlementResultV1 result, String expected) { verify(calculateResultHash(result), expected); }
    public void verifyReportHash(ReportCommitment report, String expected) { verify(calculateReportHash(report), expected); }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private static void verify(String actual, String expected) {
        if (expected == null || !expected.matches("[a-f0-9]{64}")
                || !MessageDigest.isEqual(HexFormat.of().parseHex(actual), HexFormat.of().parseHex(expected)))
            throw new IllegalArgumentException("Inspection commitment mismatch");
    }
}
