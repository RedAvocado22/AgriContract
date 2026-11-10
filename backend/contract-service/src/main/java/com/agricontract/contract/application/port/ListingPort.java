package com.agricontract.contract.application.port;

import com.agricontract.contract.application.dto.ListingReservation;
import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.application.exception.ListingNotFoundException;
import com.agricontract.contract.application.exception.ListingServiceUnavailableException;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.terms.CoffeeGoodsTerms;

import java.math.BigDecimal;

/**
 * product-service, the only owner of listing inventory and reservations (product design §8b). The create methods
 * fail closed: a 409 from product-service becomes {@link ContractConflictException} with product-service's code
 * (INSUFFICIENT_LISTING_QUANTITY, LISTING_VERSION_MISMATCH, LISTING_STATE_CONFLICT, RESERVATION_STATE_CONFLICT), an
 * unknown listing {@link ListingNotFoundException}, anything else {@link ListingServiceUnavailableException}.
 */
public interface ListingPort {
    ListingResponse getListing(String listingId);

    void closeListing(String listingId);

    /** getListingContractSnapshot: the goods terms at the buyer's listing version, stored unchanged as goodsTerms. */
    CoffeeGoodsTerms contractSnapshot(String listingId, String listingVersionToken);

    /** reserveListingQuantity: atomic, idempotent by contractId. */
    ListingReservation reserve(String contractId, String listingId, BigDecimal requestedQuantity,
                               String listingVersionToken);

    /**
     * compensateListingReservation after the local create failed. Returns normally when released, already released
     * or never reserved (404). A COMMITTED or different reservation is a {@link ContractConflictException}.
     */
    void releaseReservation(String contractId, String listingId, BigDecimal reservedQuantity);
}
