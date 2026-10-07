package com.agricontract.user.application.dto;

// actorId comes from the gateway-injected X-User-Id (jwt.sub), never from the request body.
public record RejectUserCommand(
        String targetUserId,
        String actorId,
        String reason
) {
}
