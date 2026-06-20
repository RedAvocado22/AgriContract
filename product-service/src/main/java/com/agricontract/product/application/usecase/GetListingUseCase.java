package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;


public interface GetListingUseCase {

    ListingResponse execute(String listingId);
}