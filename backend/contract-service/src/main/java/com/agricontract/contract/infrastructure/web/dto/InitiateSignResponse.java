package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.application.dto.SignChallengeView;

import java.time.Instant;
import java.util.UUID;

/** InitiateSignResponse: bare body, no ApiResponse wrapper (D32). expiresAt is UTC. */
public record InitiateSignResponse(UUID otpId, Instant expiresAt) {
    public static InitiateSignResponse from(SignChallengeView view) {
        return new InitiateSignResponse(view.otpId(), view.expiresAt());
    }
}
