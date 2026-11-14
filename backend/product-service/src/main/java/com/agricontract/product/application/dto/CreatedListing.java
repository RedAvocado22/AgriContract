package com.agricontract.product.application.dto;

/**
 * Result of CreateListing: {@code created} is false when an earlier request with the same Idempotency-Key (or the
 * same client-chosen listingId) already created this listing and it is only returned again.
 */
public record CreatedListing(ListingResponse listing, boolean created) {
}
