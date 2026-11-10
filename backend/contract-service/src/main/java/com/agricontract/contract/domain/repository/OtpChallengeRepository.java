package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.consent.ConsentActionType;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository {

    /**
     * Locks the contract row (serialises initiate/resend of both parties on this contract), then expires every
     * still-usable challenge of this signer for this contract content. Must run in the transaction that inserts
     * the new challenge, so "kill old + insert new" commits atomically.
     */
    int expireUsableChallenges(String contractId, String userId, SignerRole signerRole, ConsentActionType action,
                               String signedContentHash, Instant now, int maxAttempts);

    void insert(OtpChallenge challenge);

    /** Newest sentAt of this signer's challenges for this action on this contract (any content). Read under the lock. */
    Optional<Instant> lastSentAt(String contractId, String userId, SignerRole signerRole, ConsentActionType action);

    /** How many challenges this signer was sent on this contract since the given instant. Read under the lock. */
    int countSentSince(String contractId, String userId, SignerRole signerRole, ConsentActionType action,
                       Instant since);

    /**
     * Kills every still-usable challenge on this contract, of both parties and any action (#233: the offer was
     * withdrawn). Run under the contract row lock. Returns how many were killed.
     */
    int expireAllUsable(String contractId, Instant now);

    /** Kills one challenge, e.g. when the OTP email could not be handed to the provider. */
    void expire(UUID otpId, Instant now);

    Optional<OtpChallenge> findById(UUID otpId);

    /** Locks the challenge row until the verify transaction ends. */
    Optional<OtpChallenge> findByIdForUpdate(UUID otpId);

    /** One more wrong code; returns the new attempt count. Must commit even though the request is refused. */
    int recordFailedAttempt(UUID otpId);

    /** The right code was entered: the challenge can never be answered again. */
    void markVerified(UUID otpId, Instant verifiedAt);
}
