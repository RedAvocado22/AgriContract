package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingSearchQuery;
import com.agricontract.product.application.dto.ListingSearchResult;

public interface ListActiveListingsUseCase {
    ListingSearchResult execute(ListingSearchQuery query);
}
