package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

// updateListing by a seller who does not own the listing (contract: 403 Not the listing seller).
public class NotListingSellerException extends ApiException {
    public NotListingSellerException() {
        super(HttpStatus.FORBIDDEN, "NOT_LISTING_SELLER", "Only the seller who owns this listing can edit it");
    }
}
