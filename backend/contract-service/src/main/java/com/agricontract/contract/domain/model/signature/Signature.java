package com.agricontract.contract.domain.model.signature;

import com.agricontract.contract.domain.model.consent.SignerRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One party's signature on one contract (signature design §3). A value of the Contract aggregate, created only by
 * {@code Contract.sign} after the OTP of {@code otpId} was verified. ipAddress is null until the gateway forwards
 * a client IP it has checked.
 */
public record Signature(
        UUID signatureId,
        String contractId,
        SignerRole signerRole,
        String signerUserId,
        UUID otpId,
        UUID consentId,
        Instant authTime,
        Instant signedAt,
        String signedContentHash,
        String ipAddress) {
}
