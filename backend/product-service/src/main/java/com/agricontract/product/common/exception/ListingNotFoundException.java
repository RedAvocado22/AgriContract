package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

public class ListingNotFoundException extends ApiException {
    public ListingNotFoundException(String listingId) {
        super(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "Listing with id " + listingId + " not found");
    }
}
