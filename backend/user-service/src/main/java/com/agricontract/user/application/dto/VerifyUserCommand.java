package com.agricontract.user.application.dto;

import java.time.Instant;

// actorId is the authenticated principal (gateway X-User-Id = jwt.sub), never from the request body.
public record VerifyUserCommand(
        String targetUserId,
        String actorId,
        Instant authorizationExpiresAt
) {
}
