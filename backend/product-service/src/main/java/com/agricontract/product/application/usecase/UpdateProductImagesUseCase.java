package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ProductResponse;

import java.util.List;

public interface UpdateProductImagesUseCase {
    ProductResponse execute(String productId, List<String> imageUrls);
}
