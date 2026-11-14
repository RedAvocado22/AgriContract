package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ProductResponse;

public interface GetProductUseCase {
    ProductResponse execute(String productId);
}
