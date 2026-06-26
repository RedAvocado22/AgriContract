package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;

public interface CreateListingUseCase {
    ListingResponse execute(String sellerId, CreateListingRequest request);
}
