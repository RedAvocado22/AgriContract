package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListProductsUseCase {
    Page<ProductResponse> execute(Pageable pageable);
}
