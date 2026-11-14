package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategorySummary;
import com.agricontract.product.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Design §4: APPROVED only, ordered by name, not paged (the catalog is small). PENDING/REJECTED never show here.
@Service
@RequiredArgsConstructor
public class ListApprovedCategoriesUseCaseImpl implements ListApprovedCategoriesUseCase {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategorySummary> execute() {
        return categoryRepository.findAllApprovedOrderByName().stream().map(CategorySummary::from).toList();
    }
}
