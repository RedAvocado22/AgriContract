package com.agricontract.user.domain.model;

import com.agricontract.user.domain.exception.EmailVerificationConflictException;

import java.time.Duration;
import java.time.Instant;

// Design §4.4: a code lives expirySeconds, allows maxAttempts wrong guesses, and a new one waits resendCooldownSeconds.
public record EmailVerificationPolicy(long expirySeconds, int maxAttempts, long resendCooldownSeconds) {

    public EmailVerificationPolicy {
        if (expirySeconds <= 0 || maxAttempts <= 0 || resendCooldownSeconds < 0) {
            throw new IllegalArgumentException("Email verification expiry and attempts must be positive, cooldown >= 0");
        }
    }

    // previous: the pending challenge being replaced, or null. Counted even if its email never arrived.
    public void ensureCanResend(EmailVerificationChallenge previous, Instant now) {
        if (previous == null || resendCooldownSeconds == 0) {
            return;
        }
        Instant allowedAt = previous.getSentAt().plusSeconds(resendCooldownSeconds);
        if (now.isBefore(allowedAt)) {
            long wait = Math.max(1, Duration.between(now, allowedAt).toSeconds());
            throw new EmailVerificationConflictException(EmailVerificationConflictException.RESEND_TOO_SOON,
                    "A code was sent less than " + resendCooldownSeconds + "s ago; try again in " + wait + "s");
        }
    }
}
