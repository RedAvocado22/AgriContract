package com.agricontract.product.domain.model.vo;

// Set only when geoVerificationStatus = CHECKED; a label for buyers, never a block (design §2.2.4).
public enum GeoRiskLevel {
    LOW_RISK,
    HIGH_RISK,
    INCONCLUSIVE
}
