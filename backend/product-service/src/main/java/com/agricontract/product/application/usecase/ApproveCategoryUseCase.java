package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;

public interface ApproveCategoryUseCase {
    CategoryResponse execute(String categoryId);
}
