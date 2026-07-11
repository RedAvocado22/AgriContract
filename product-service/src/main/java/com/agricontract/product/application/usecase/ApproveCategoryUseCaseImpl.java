package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.common.exception.CategoryNotFoundException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApproveCategoryUseCaseImpl implements ApproveCategoryUseCase {

    private final CategoryRepository categoryRepository;

    @Override
    public CategoryResponse execute(String categoryId) {
        Category category = categoryRepository.findById(new CategoryId(categoryId))
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        category.approve();

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId().value())
                .name(category.getName())
                .status(category.getStatus())
                .rejectionReason(category.getRejectionReason())
                .proposedBy(category.getProposedBy())
                .build();
    }
}
