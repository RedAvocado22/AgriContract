package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;

public interface UpdateListingUseCase {
    ListingResponse execute(String sellerId, String listingId, String idempotencyKey, CreateListingRequest request);
}
