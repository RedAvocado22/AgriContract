package com.agricontract.user.domain.model.vo;

// Result of UserProfile.assignRole. APPLIED and REJECTED_* are audited (role_change_audit.outcome); NO_CHANGE never is.
public enum RoleChangeOutcome {
    APPLIED,
    // The profile already holds the requested role.
    NO_CHANGE,
    // Refused because the requested role is separated from the current one (OPERATOR <-> ARBITRATOR, #261).
    REJECTED_ROLE_SEPARATION
}
