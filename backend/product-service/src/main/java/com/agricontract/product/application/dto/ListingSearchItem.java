package com.agricontract.product.application.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * One search hit: the unchanged listing fields plus {@code sellerReputation}. Null reputation means unknown
 * (anonymous search or the read failed); an unrated seller is {@code {score: null, locked: false}}.
 */
public record ListingSearchItem(@JsonUnwrapped ListingResponse listing, SellerReputation sellerReputation) {
}
