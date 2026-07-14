package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.CategoryId;

import java.util.Optional;

public interface CategoryRepository {
    Category save(Category category);

    Optional<Category> findById(CategoryId categoryId);

    Optional<Category> findByNormalizedName(String normalizedName);
}
