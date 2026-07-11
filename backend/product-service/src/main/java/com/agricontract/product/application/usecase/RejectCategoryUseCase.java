package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;

public interface RejectCategoryUseCase {
    CategoryResponse execute(String categoryId, String reason);
}
