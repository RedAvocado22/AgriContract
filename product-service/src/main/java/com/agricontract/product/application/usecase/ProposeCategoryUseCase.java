package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.ProposeCategoryRequest;

public interface ProposeCategoryUseCase {
    CategoryResponse execute(ProposeCategoryRequest request, String proposedBy, String proposedByEmail);
}
