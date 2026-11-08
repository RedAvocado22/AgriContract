package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.consent.SignerRole;

import java.time.Instant;
import java.util.UUID;

/** D42 cancel request commands. authTime: the caller's JWT auth_time (step-up), null if the gateway sent none. */
public final class ActivationCancelCommands {
    private ActivationCancelCommands() {
    }

    public record Initiate(String contractId, String actorId, UUID idempotencyKey, SignerRole requesterRole,
                           String consentStatementVersion, String presentedContentHash, Instant authTime) {
    }

    public record Verify(String contractId, String actorId, UUID idempotencyKey, UUID otpId, String otpCode,
                         Instant authTime) {
    }

    public record Withdraw(String contractId, String actorId, UUID idempotencyKey) {
    }
}
