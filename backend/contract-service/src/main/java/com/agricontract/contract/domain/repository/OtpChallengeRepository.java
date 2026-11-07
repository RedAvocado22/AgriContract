package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.signature.OtpChallenge;

import com.agricontract.contract.domain.model.consent.SignerRole;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository {

    /**
     * Locks the contract row (serialises initiate/resend of both parties on this contract), then expires every
     * still-usable challenge of this signer for this contract content. Must run in the transaction that inserts
     * the new challenge, so "kill old + insert new" commits atomically.
     */
    int expireUsableChallenges(String contractId, String userId, SignerRole signerRole, String signedContentHash,
                               Instant now, int maxAttempts);

    void insert(OtpChallenge challenge);

    /** Kills one challenge, e.g. when the OTP email could not be handed to the provider. */
    void expire(UUID otpId, Instant now);

    Optional<OtpChallenge> findById(UUID otpId);
}
