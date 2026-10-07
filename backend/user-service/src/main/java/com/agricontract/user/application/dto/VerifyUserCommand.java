package com.agricontract.user.application.dto;

import java.time.Instant;

// actorId comes from the gateway-injected X-User-Id (jwt.sub), never from the request body.
public record VerifyUserCommand(
        String targetUserId,
        String actorId,
        Instant authorizationExpiresAt
) {
}
