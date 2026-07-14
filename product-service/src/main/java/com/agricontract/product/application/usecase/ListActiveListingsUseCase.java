package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.PagedResult;
import org.springframework.data.domain.Pageable;

public interface ListActiveListingsUseCase {
    PagedResult<ListingResponse> execute(Pageable pageable);
}
