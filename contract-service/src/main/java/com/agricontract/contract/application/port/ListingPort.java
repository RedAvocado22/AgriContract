package com.agricontract.contract.application.port;

import com.agricontract.contract.application.dto.ListingResponse;

public interface ListingPort {
    ListingResponse getListing(String listingId);
    void closeListing(String listingId);
}
