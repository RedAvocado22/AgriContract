package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListActiveListingsUseCase {
    Page<ListingResponse> execute(Pageable pageable);

}
