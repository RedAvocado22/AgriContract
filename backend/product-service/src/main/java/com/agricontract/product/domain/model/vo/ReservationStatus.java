package com.agricontract.product.domain.model.vo;

// RESERVED -> SIGNED_RELEASABLE -> COMMITTED; RESERVED | SIGNED_RELEASABLE -> RELEASED
public enum ReservationStatus {
    RESERVED,
    SIGNED_RELEASABLE,
    COMMITTED,
    RELEASED;

    public boolean canTransitionTo(ReservationStatus target) {
        return switch (this) {
            case RESERVED -> target == SIGNED_RELEASABLE || target == RELEASED;
            case SIGNED_RELEASABLE -> target == COMMITTED || target == RELEASED;
            case COMMITTED, RELEASED -> false;
        };
    }
}
