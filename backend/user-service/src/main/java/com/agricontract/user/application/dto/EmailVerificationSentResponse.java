package com.agricontract.user.application.dto;

import java.time.Instant;

public record EmailVerificationSentResponse(Instant expiresAt) {
}
