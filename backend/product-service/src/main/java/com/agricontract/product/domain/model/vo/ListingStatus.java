package com.agricontract.product.domain.model.vo;

/**
 * Contract ListingResponse.status. Only AVAILABLE, PARTIALLY_COMMITTED and CLOSED are produced in this phase; DRAFT
 * and PAUSED exist to match the contract (design §8b.1).
 */
public enum ListingStatus {
    DRAFT,
    AVAILABLE,
    PARTIALLY_COMMITTED,
    PAUSED,
    CLOSED;

    /** The states a reserve accepts and a search lists. */
    public boolean isOpenForReservation() {
        return this == AVAILABLE || this == PARTIALLY_COMMITTED;
    }
}
