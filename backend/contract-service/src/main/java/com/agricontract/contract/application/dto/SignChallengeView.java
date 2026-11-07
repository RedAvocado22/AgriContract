package com.agricontract.contract.application.dto;

import java.time.Instant;
import java.util.UUID;

/** InitiateSignResponse: which challenge to answer and until when. */
public record SignChallengeView(UUID otpId, Instant expiresAt) {
}
