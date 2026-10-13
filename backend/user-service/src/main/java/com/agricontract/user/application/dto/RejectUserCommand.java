package com.agricontract.user.application.dto;

// actorId is the authenticated principal (gateway X-User-Id = jwt.sub), never from the request body.
public record RejectUserCommand(
        String targetUserId,
        String actorId,
        String reason
) {
}
