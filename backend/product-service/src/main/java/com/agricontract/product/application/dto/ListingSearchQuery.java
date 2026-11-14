package com.agricontract.product.application.dto;

import com.agricontract.product.domain.repository.PageQuery;

import java.util.Objects;

/**
 * @param minSellerScore null = no reputation filter
 * @param viewer         null = anonymous search
 */
public record ListingSearchQuery(PageQuery page, Integer minSellerScore, ReputationViewer viewer) {
    public ListingSearchQuery {
        Objects.requireNonNull(page, "page");
    }
}
