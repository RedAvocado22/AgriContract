package com.agricontract.contract.domain.model.signature;

import com.agricontract.contract.domain.exception.ContractConflictException;

import java.time.Duration;
import java.time.Instant;

/**
 * signature design §4.1: otpExpirySeconds (300), otpMaxAttempts (5), otpResendCooldownSeconds (60) and
 * otpMaxResendPerHour (5). The send limits count every challenge issued to one signer on one contract, also those
 * whose email failed: such an email may still arrive.
 */
public record OtpPolicy(long expirySeconds, int maxAttempts, long resendCooldownSeconds, int maxSendsPerHour) {
    public static final String RESEND_TOO_SOON = "OTP_RESEND_TOO_SOON";
    public static final String RESEND_LIMIT = "OTP_RESEND_LIMIT_REACHED";

    public OtpPolicy {
        if (expirySeconds <= 0 || maxAttempts <= 0 || resendCooldownSeconds < 0 || maxSendsPerHour <= 0) {
            throw new IllegalArgumentException("OTP expiry, attempts and hourly sends must be positive, cooldown >= 0");
        }
    }

    /** lastSentAt: newest earlier challenge of this signer on this contract, or null; sentLastHour: their count. */
    public void ensureCanSend(Instant lastSentAt, int sentLastHour, Instant now) {
        if (resendCooldownSeconds > 0 && lastSentAt != null && now.isBefore(lastSentAt.plusSeconds(resendCooldownSeconds))) {
            long wait = Math.max(1, Duration.between(now, lastSentAt.plusSeconds(resendCooldownSeconds))
                    .toSeconds());
            throw new ContractConflictException(RESEND_TOO_SOON,
                    "A code was sent less than " + resendCooldownSeconds + "s ago; try again in " + wait + "s.");
        }
        if (sentLastHour >= maxSendsPerHour) {
            throw new ContractConflictException(RESEND_LIMIT,
                    "At most " + maxSendsPerHour + " codes per hour; try again later.");
        }
    }
}
