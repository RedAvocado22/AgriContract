package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingContractSnapshotResponse;

public interface GetListingContractSnapshotUseCase {
    ListingContractSnapshotResponse execute(String listingId, String listingVersionToken);
}
