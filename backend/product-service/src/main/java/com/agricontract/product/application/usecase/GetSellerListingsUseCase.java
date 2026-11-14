package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.domain.repository.PageQuery;

public interface GetSellerListingsUseCase {
    PagedResult<ListingResponse> execute(String sellerId, PageQuery page);
}
