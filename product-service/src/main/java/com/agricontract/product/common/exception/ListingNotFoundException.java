package com.agricontract.product.common.exception;

public class ListingNotFoundException extends RuntimeException {
    public ListingNotFoundException(String listingId) {
        super("Listing with id " + listingId + " not found");
    }
}
