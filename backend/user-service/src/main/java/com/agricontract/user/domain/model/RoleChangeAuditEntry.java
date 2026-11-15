package com.agricontract.user.domain.model;

import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.RoleChangeOutcome;

import java.time.Instant;

// One role change attempt worth keeping: an applied change (#215) or a refused OPERATOR <-> ARBITRATOR assignment
// (#261). For a refused one newRole is the requested role and the profile kept oldRole. No-op assignments are not recorded.
public record RoleChangeAuditEntry(
        String actorId,
        String targetUserId,
        Role oldRole,
        Role newRole,
        RoleChangeOutcome outcome,
        Instant changedAt
) {
    public static RoleChangeAuditEntry applied(String actorId, String targetUserId, Role oldRole, Role newRole,
                                               Instant changedAt) {
        return new RoleChangeAuditEntry(actorId, targetUserId, oldRole, newRole, RoleChangeOutcome.APPLIED, changedAt);
    }

    public static RoleChangeAuditEntry rejectedForSeparation(String actorId, String targetUserId, Role currentRole,
                                                             Role requestedRole, Instant attemptedAt) {
        return new RoleChangeAuditEntry(actorId, targetUserId, currentRole, requestedRole,
                RoleChangeOutcome.REJECTED_ROLE_SEPARATION, attemptedAt);
    }
}
