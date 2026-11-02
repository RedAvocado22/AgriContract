package com.agricontract.product.application.dto;

import java.util.Map;

/**
 * Result of one batch of public-summary reads. A seller missing from {@code bySeller} is unknown (the read failed);
 * {@code complete} is false as soon as one read failed.
 */
public record ReputationLookup(Map<String, SellerReputation> bySeller, boolean complete) {
    public ReputationLookup {
        bySeller = Map.copyOf(bySeller);
    }
}
