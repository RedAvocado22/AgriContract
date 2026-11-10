package com.agricontract.contract.application.exception;

public class ListingNotFoundException extends RuntimeException {
    public ListingNotFoundException(String listingId) {
        super("Listing not found: " + listingId);
    }
}
