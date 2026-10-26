package com.agricontract.inspection.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Already verified material from the future confirmation workflow, never a public request DTO.
 * This persistence boundary does not calculate or verify cryptographic commitments.
 */
public record ReportEvidence(String content, String normalizedResult, String reportFileHash,
                             String resultHash, String reportHash, Instant confirmedAt) {
    public ReportEvidence {
        if (content == null || content.isBlank() || normalizedResult == null || normalizedResult.isBlank())
            throw new IllegalArgumentException("Report content and normalized result are required");
        for (String hash : new String[]{reportFileHash, resultHash, reportHash}) {
            if (hash == null || !hash.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("A complete SHA-256 commitment is required");
        }
        Objects.requireNonNull(confirmedAt, "confirmedAt");
    }
}
