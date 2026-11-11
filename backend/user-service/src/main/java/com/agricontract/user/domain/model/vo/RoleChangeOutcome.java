package com.agricontract.user.domain.model.vo;

public enum RoleChangeOutcome {
    APPLIED,
    // Refused because the requested role is separated from the current one (OPERATOR <-> ARBITRATOR, #261).
    REJECTED_ROLE_SEPARATION
}
