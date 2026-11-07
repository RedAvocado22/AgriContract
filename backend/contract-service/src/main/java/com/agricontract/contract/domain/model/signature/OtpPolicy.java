package com.agricontract.contract.domain.model.signature;

/** signature design §4.1: otpExpirySeconds (300) and otpMaxAttempts (5). */
public record OtpPolicy(long expirySeconds, int maxAttempts) {
    public OtpPolicy {
        if (expirySeconds <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("OTP expiry and max attempts must be positive");
        }
    }
}
