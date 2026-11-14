package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.vo.ListingId;

import java.util.Optional;

/**
 * Idempotency-Keys of applied updateListing requests, unique per seller. Recorded in the same transaction as the edit,
 * so a key exists exactly when its edit committed.
 */
public interface ListingEditKeyRepository {

    record EditKey(ListingId listingId, String requestFingerprint) {
    }

    Optional<EditKey> find(String sellerId, String idempotencyKey);

    /** A second record for the same seller and key fails with WriteConflictException. */
    void record(String sellerId, String idempotencyKey, EditKey editKey);
}
