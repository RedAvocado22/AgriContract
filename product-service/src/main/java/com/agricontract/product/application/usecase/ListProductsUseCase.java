package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.application.dto.ProductResponse;
import org.springframework.data.domain.Pageable;

public interface ListProductsUseCase {
    PagedResult<ProductResponse> execute(Pageable pageable);
}
