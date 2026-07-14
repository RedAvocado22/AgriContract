package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.ProposeCategoryRequest;
import com.agricontract.product.common.exception.DuplicateCategoryException;
import com.agricontract.product.common.util.TextNormalizer;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProposeCategoryUseCaseImpl implements ProposeCategoryUseCase {

    private final CategoryRepository categoryRepository;

    @Override
    public CategoryResponse execute(ProposeCategoryRequest request, String proposedBy, String proposedByEmail) {
        String normalizedName = TextNormalizer.normalize(request.name());

        // Duplicate blocks regardless of existing status (PENDING/APPROVED/REJECTED all count).
        if (categoryRepository.findByNormalizedName(normalizedName).isPresent()) {
            throw new DuplicateCategoryException(request.name());
        }

        CategoryId categoryId = (request.categoryId() != null && !request.categoryId().isBlank())
                ? new CategoryId(request.categoryId())
                : new CategoryId(UUID.randomUUID().toString());

        Category category = Category.propose(categoryId, request.name(), normalizedName, proposedBy, proposedByEmail);

        Category saved;
        try {
            saved = categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            // uq_categories_normalized_name tripped by a concurrent propose of the same name.
            throw new DuplicateCategoryException(request.name());
        }

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
