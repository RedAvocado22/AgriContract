package com.agricontract.contract.infrastructure.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** pepper: secret mixed into every OTP hash; from the OTP_PEPPER env var outside local dev. */
@ConfigurationProperties(prefix = "contract.otp")
public record OtpProperties(long expirySeconds, int maxAttempts, String pepper) {
}
