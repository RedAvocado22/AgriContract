package com.agricontract.user.domain.model.vo;

public enum Role {
    SELLER,
    BUYER,
    ADMIN,
    INSPECTOR,
    OPERATOR,
    ARBITRATOR;

    // OPERATOR proposes and ARBITRATOR approves maker-checker actions, so one account must never hold both
    // (data-governance-phase2-design.md §5.2).
    public boolean isSeparatedFrom(Role other) {
        return (this == OPERATOR && other == ARBITRATOR) || (this == ARBITRATOR && other == OPERATOR);
    }
}
