package com.agricontract.contract.application.service;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.model.consent.ConsentStatements;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.repository.ConsentEvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Step one of InitiateSign (#94, wired into the endpoint in #96): validate and append the checkbox consent. Runs in
 * the caller's transaction, the same one that creates the OTP challenge, so an OTP never exists without consent.
 * The OTP email is sent only after that transaction commits; if it fails the consent stays (D35).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SigningConsentRecorder {
    private final ConsentEvidenceRepository consentEvidenceRepository;
    private final ConsentStatements consentStatements;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public ConsentEvidence record(Contract contract, String actorId, SignerRole signerRole,
                                  String statementVersion, String presentedContentHash, String correlationId) {
        ConsentEvidence evidence = contract.consentToSign(actorId, signerRole, statementVersion,
                presentedContentHash, consentStatements, UUID.randomUUID(), Instant.now(clock), correlationId);
        consentEvidenceRepository.append(evidence);
        log.info("Consent {} to sign contract {} by {} as {} on {}", evidence.consentId(), evidence.subjectId(),
                actorId, signerRole, presentedContentHash);
        return evidence;
    }

    /** D42 cancel request: same append-only evidence, CANCEL_ACTIVATION statement. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConsentEvidence recordCancelActivation(Contract contract, String actorId, SignerRole role,
                                                  String statementVersion, String presentedContentHash,
                                                  String correlationId) {
        ConsentEvidence evidence = contract.consentToCancelActivation(actorId, role, statementVersion,
                presentedContentHash, consentStatements, UUID.randomUUID(), Instant.now(clock), correlationId);
        consentEvidenceRepository.append(evidence);
        log.info("Consent {} to cancel contract {} by {} as {}", evidence.consentId(), evidence.subjectId(), actorId,
                role);
        return evidence;
    }
}
