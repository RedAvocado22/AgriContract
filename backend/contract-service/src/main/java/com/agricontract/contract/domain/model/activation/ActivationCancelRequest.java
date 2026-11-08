package com.agricontract.contract.domain.model.activation;

import com.agricontract.contract.domain.model.consent.SignerRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One party's OTP-confirmed request to stop a SIGNED contract whose deposit lock failed (D42). It stands until the
 * party withdraws it (withdrawnAt) or the refund starts; a withdrawal is recorded, never deleted.
 */
public record ActivationCancelRequest(UUID requestId, String contractId, SignerRole partyRole, String userId,
                                      UUID otpId, Instant requestedAt, Instant withdrawnAt) {

    public static ActivationCancelRequest stand(String contractId, SignerRole partyRole, String userId, UUID otpId,
                                                Instant now) {
        return new ActivationCancelRequest(UUID.randomUUID(), contractId, partyRole, userId, otpId, now, null);
    }

    public boolean isStanding() {
        return withdrawnAt == null;
    }
}
