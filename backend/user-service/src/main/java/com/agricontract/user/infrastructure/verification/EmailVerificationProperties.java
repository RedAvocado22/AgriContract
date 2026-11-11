package com.agricontract.user.infrastructure.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;

// pepper: secret mixed into every code hash; from EMAIL_VERIFICATION_PEPPER outside local dev.
@ConfigurationProperties(prefix = "user.email-verification")
public record EmailVerificationProperties(long expirySeconds, int maxAttempts, long resendCooldownSeconds,
                                          String pepper) {
}
