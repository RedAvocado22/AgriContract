package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.domain.model.vo.Commodity;

public interface ApproveCategoryUseCase {
    CategoryResponse execute(String categoryId, Commodity commodity);
}
