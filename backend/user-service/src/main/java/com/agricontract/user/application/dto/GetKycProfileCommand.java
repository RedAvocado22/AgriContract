package com.agricontract.user.application.dto;

// actorId and actorRole come from the authenticated principal (gateway X-User-Id / X-User-Role), never from the request.
public record GetKycProfileCommand(
        String targetUserId,
        String actorId,
        String actorRole
) {
}
