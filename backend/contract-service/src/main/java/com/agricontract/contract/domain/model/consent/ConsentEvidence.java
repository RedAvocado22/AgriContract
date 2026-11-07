package com.agricontract.contract.domain.model.consent;

import java.time.Instant;
import java.util.UUID;

/**
 * Proof that one user ticked one specific statement while looking at one specific content (presentedContentHash).
 * Append-only: never updated or deleted. A consent alone signs nothing; the Signature (OTP verified) points to it.
 */
public record ConsentEvidence(
        UUID consentId,
        ConsentActionType actionType,
        ConsentSubjectType subjectType,
        String subjectId,
        String actorUserId,
        SignerRole actorRole,
        String statementCode,
        String statementVersion,
        String statementHash,
        String presentedContentHash,
        Instant consentedAt,
        String correlationId) {
}
