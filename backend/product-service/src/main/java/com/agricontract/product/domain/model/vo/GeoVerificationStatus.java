package com.agricontract.product.domain.model.vo;

// Progress of the forest-baseline check, not its result (design §2.2.4). Only PENDING blocks a listing:
// CHECKED and UNVERIFIED both have an outcome.
public enum GeoVerificationStatus {
    PENDING,
    CHECKED,
    UNVERIFIED
}
