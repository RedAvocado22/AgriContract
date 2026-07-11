package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateProductRequest;
import com.agricontract.product.application.dto.ProductResponse;

public interface CreateProductUseCase {
    ProductResponse execute(CreateProductRequest request);
}
