package com.agricontract.product.application.dto;

import org.springframework.data.domain.Pageable;

import java.util.Objects;

/**
 * @param minSellerScore null = no reputation filter
 * @param viewer         null = anonymous search
 */
public record ListingSearchQuery(Pageable pageable, Integer minSellerScore, ReputationViewer viewer) {
    public ListingSearchQuery {
        Objects.requireNonNull(pageable, "pageable");
    }
}
