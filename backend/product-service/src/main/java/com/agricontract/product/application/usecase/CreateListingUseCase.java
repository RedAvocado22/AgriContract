package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.CreatedListing;

public interface CreateListingUseCase {
    CreatedListing execute(String sellerId, String idempotencyKey, CreateListingRequest request);
}
