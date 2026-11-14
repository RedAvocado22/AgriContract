package com.agricontract.product.domain.model.vo;

/**
 * Why a CLOSED listing is closed (column listings.close_reason). Only INVENTORY_EXHAUSTED may be reopened by a
 * reservation release; any other close holds until the seller edits the listing again (design §8b.1).
 * Legacy rows closed by the removed Phase 1 close endpoint have no reason (null) and are not reopened either.
 */
public enum CloseReason {
    // a reserve took the last stock
    INVENTORY_EXHAUSTED,
    // the seller lowered offeredQuantity to the reserved total (D48)
    SELLER_CLOSED,
    // an EXPIRED Phase 1 row, mapped by V11 when the delivery-deadline expiry was removed
    LEGACY_EXPIRED
}
