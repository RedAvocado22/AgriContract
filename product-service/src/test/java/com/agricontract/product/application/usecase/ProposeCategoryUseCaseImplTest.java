package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.ProposeCategoryRequest;
import com.agricontract.product.common.exception.DuplicateCategoryException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposeCategoryUseCaseImplTest {

    @Mock private CategoryRepository categoryRepository;

    private ProposeCategoryUseCase useCase;

    @Test
    void execute_newName_savesAndReturnsPending() {
        useCase = new ProposeCategoryUseCaseImpl(categoryRepository);
        when(categoryRepository.findByNormalizedName("ca phe")).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = useCase.execute(
                new ProposeCategoryRequest("category-1", "Cà Phê"), "seller-1", "seller@test.com");

        assertThat(response.categoryId()).isEqualTo("category-1");
        assertThat(response.name()).isEqualTo("Cà Phê");
        verify(categoryRepository).save(any());
    }

    @Test
    void execute_duplicateNormalizedName_throwsRegardlessOfExistingStatus() {
        useCase = new ProposeCategoryUseCaseImpl(categoryRepository);
        Category existing = Category.propose(new CategoryId("category-0"), "ca phe", "ca phe", "other", "other@test.com");
        when(categoryRepository.findByNormalizedName("ca phe")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(
                new ProposeCategoryRequest(null, "Ca Phe"), "seller-1", "seller@test.com"))
                .isInstanceOf(DuplicateCategoryException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void execute_concurrentDuplicateRace_dbConstraintMappedToDuplicateException() {
        useCase = new ProposeCategoryUseCaseImpl(categoryRepository);
        when(categoryRepository.findByNormalizedName("ca phe")).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenThrow(new DataIntegrityViolationException("uq_categories_normalized_name"));

        assertThatThrownBy(() -> useCase.execute(
                new ProposeCategoryRequest(null, "Ca Phe"), "seller-1", "seller@test.com"))
                .isInstanceOf(DuplicateCategoryException.class);
    }
}
