package com.agricontract.product.application.dto;

/** How far seller reputation could be applied to one search page. */
public enum ReputationStatus {
    /** Every seller on the page was read. */
    AVAILABLE,
    /** At least one read failed: those sellers carry no reputation and were not filtered out. */
    UNAVAILABLE,
    /** Anonymous search: the public summary is authenticated-only, so nothing was read. */
    SIGN_IN_REQUIRED
}
