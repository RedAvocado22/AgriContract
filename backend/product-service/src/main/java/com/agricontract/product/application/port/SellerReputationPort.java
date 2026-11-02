package com.agricontract.product.application.port;

import com.agricontract.product.application.dto.ReputationLookup;
import com.agricontract.product.application.dto.ReputationViewer;

import java.util.Set;

/** Reads reputation-service public summaries. Never throws: an outage shows up as an incomplete lookup. */
public interface SellerReputationPort {
    ReputationLookup lookup(Set<String> sellerIds, ReputationViewer viewer);
}
