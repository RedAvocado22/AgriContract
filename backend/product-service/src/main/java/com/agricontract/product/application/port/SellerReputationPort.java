package com.agricontract.product.application.port;

import com.agricontract.product.application.dto.ReputationLookup;

import java.util.Set;

/** Reads reputation-service public summaries. Never throws: an outage shows up as an incomplete lookup. */
public interface SellerReputationPort {
    /** At most 100 distinct seller ids (one search page). */
    ReputationLookup lookup(Set<String> sellerIds);
}
