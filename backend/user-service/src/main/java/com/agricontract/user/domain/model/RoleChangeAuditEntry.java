package com.agricontract.user.domain.model;

import com.agricontract.user.domain.model.vo.Role;

import java.time.Instant;

// One applied role change (#215): who changed whose role, from what to what, and when. No-op assignments are not recorded.
public record RoleChangeAuditEntry(
        String actorId,
        String targetUserId,
        Role oldRole,
        Role newRole,
        Instant changedAt
) {
}
