package com.agricontract.contract.application.service;

import com.agricontract.contract.application.dto.IssuedOtp;
import com.agricontract.contract.application.port.OtpCodes;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.ConsentActionType;
import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.model.signature.OtpPolicy;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Step two of InitiateSign (#95, endpoint in #96) and of the D42 cancel request: after the consent is appended in the
 * same transaction, kill the party's still-usable challenges for this action and content, enforce the resend cooldown
 * and hourly limit (per action), and insert a new one bound to the consented action. Only the newest challenge stays usable,
 * also when two resends race: both lock the contract row first, so the second kills the first one's challenge.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpChallengeIssuer {
    private final OtpChallengeRepository challenges;
    private final OtpCodes otpCodes;
    private final OtpPolicy policy;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedOtp issue(Contract contract, ConsentEvidence consent, Instant authTime) {
        String currentHash = contract.getCoffeeTerms().signedContentHash();
        if (!consent.subjectId().equals(contract.getContractId().value())
                || !consent.presentedContentHash().equals(currentHash)) {
            throw new IllegalStateException("Consent " + consent.consentId() + " does not cover the current terms of "
                    + contract.getContractId().value());
        }
        Instant now = now();
        ConsentActionType action = consent.actionType();
        int killed = challenges.expireUsableChallenges(consent.subjectId(), consent.actorUserId(), consent.actorRole(),
                action, currentHash, now, policy.maxAttempts());
        // Counted under the contract lock taken above, so two concurrent resends cannot both pass. A refusal here
        // rolls the whole transaction back, the kill above and the consent included.
        policy.ensureCanSend(
                challenges.lastSentAt(consent.subjectId(), consent.actorUserId(), consent.actorRole(), action)
                        .orElse(null),
                challenges.countSentSince(consent.subjectId(), consent.actorUserId(), consent.actorRole(), action,
                        now.minusSeconds(3600)),
                now);

        String code = otpCodes.newCode();
        OtpChallenge challenge = OtpChallenge.issue(UUID.randomUUID(), consent.consentId(), consent.subjectId(),
                consent.actorRole(), consent.actorUserId(), authTime, currentHash, otpCodes.hash(code), now,
                policy.expirySeconds(), action);
        challenges.insert(challenge);
        log.info("OTP challenge {} for contract {} signer {} as {}, replaced {} earlier", challenge.otpId(),
                challenge.contractId(), challenge.userId(), challenge.signerRole(), killed);
        return new IssuedOtp(challenge, code);
    }

    /** The email could not be handed over: the new code must never be usable (no late email, no ghost code). */
    @Transactional
    public void expire(UUID otpId) {
        challenges.expire(otpId, now());
        log.warn("OTP challenge {} expired because the email was not sent", otpId);
    }

    /**
     * Microsecond precision, as MySQL TIMESTAMP(6) stores. MySQL rounds extra digits, so a nanosecond "kill" instant
     * could be stored 1µs after the new challenge's sentAt and leave the old one usable at that instant.
     */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
