package com.agricontract.product.application.dto;

import com.agricontract.product.common.PaginatedResponse;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** The existing page shape plus {@code reputationStatus}, so a client can tell when the reputation filter degraded. */
public record ListingSearchResponse(@JsonUnwrapped PaginatedResponse<ListingSearchItem> page,
                                    ReputationStatus reputationStatus) {
}
