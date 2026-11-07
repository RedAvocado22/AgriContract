package com.agricontract.contract.domain.model.signature;

import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.consent.SignerRole;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One OTP sent to one signer for one contract content (signature design §6, #95). Only the HMAC of the code is
 * kept. A challenge is usable while it is not verified, not past expiresAt and below the attempt limit; a resend
 * or a failed email "kills" it by moving expiresAt to now (no extra status column, as the design requires).
 */
public record OtpChallenge(
        UUID otpId,
        UUID consentId,
        String contractId,
        SignerRole signerRole,
        String userId,
        Instant authTime,
        String signedContentHash,
        String otpHash,
        Instant sentAt,
        Instant expiresAt,
        Instant verifiedAt,
        int attemptCount) {

    public static final String OTP_EXPIRED = "OTP_EXPIRED";
    public static final String OTP_LOCKED = "OTP_ATTEMPTS_EXHAUSTED";
    public static final String OTP_USED = "OTP_ALREADY_USED";

    /** Instants are kept at microsecond precision, the precision MySQL TIMESTAMP(6) stores. */
    public static OtpChallenge issue(UUID otpId, UUID consentId, String contractId, SignerRole signerRole,
                                     String userId, Instant authTime, String signedContentHash, String otpHash,
                                     Instant now, long expirySeconds) {
        Instant sentAt = now.truncatedTo(ChronoUnit.MICROS);
        return new OtpChallenge(otpId, consentId, contractId, signerRole, userId,
                authTime.truncatedTo(ChronoUnit.MICROS), signedContentHash, otpHash, sentAt,
                sentAt.plusSeconds(expirySeconds), null, 0);
    }

    public boolean isUsable(Instant now, int maxAttempts) {
        return verifiedAt == null && now.isBefore(expiresAt) && attemptCount < maxAttempts;
    }

    /**
     * Refuses a challenge that cannot be answered any more. Applies to a challenge superseded by a resend too:
     * its expiresAt was moved to the resend instant, so the right code for it is still refused.
     */
    public void ensureUsable(Instant now, int maxAttempts) {
        if (verifiedAt != null) {
            throw new ContractConflictException(OTP_USED, "This code was already used.");
        }
        if (!now.isBefore(expiresAt)) {
            throw new ContractConflictException(OTP_EXPIRED, "This code has expired or was replaced; request a new one.");
        }
        if (attemptCount >= maxAttempts) {
            throw new ContractConflictException(OTP_LOCKED, "Too many wrong codes; request a new one.");
        }
    }
}
