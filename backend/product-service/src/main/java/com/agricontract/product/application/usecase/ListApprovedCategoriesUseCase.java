package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategorySummary;

import java.util.List;

public interface ListApprovedCategoriesUseCase {
    List<CategorySummary> execute();
}
