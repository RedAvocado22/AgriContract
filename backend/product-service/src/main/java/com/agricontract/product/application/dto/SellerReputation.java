package com.agricontract.product.application.dto;

/**
 * The two fields of the reputation-service public summary that listing search uses. {@code score} is null for a
 * seller with no history (unrated, not zero). Lock history and anything else in the summary is never copied.
 */
public record SellerReputation(Integer score, boolean locked) {
    public SellerReputation {
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("score must be within 0..100");
        }
    }
}
