package com.agricontract.contract.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * POST /contracts/{id}/sign/verify. authTime: the caller's last password entry (JWT auth_time), null if the gateway
 * did not pass it. ipAddress: null until the gateway forwards a checked client IP.
 */
public record VerifySignCommand(
        String contractId,
        String actorId,
        UUID idempotencyKey,
        UUID otpId,
        String otpCode,
        Instant authTime,
        String ipAddress) {

    /** The code must never reach a log line through this record. */
    @Override
    public String toString() {
        return "VerifySignCommand[contractId=" + contractId + ", actorId=" + actorId + ", idempotencyKey="
                + idempotencyKey + ", otpId=" + otpId + "]";
    }
}
