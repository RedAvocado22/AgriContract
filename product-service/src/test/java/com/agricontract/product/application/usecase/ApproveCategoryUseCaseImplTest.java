package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.common.exception.CategoryNotFoundException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CategoryStatus;
import com.agricontract.product.domain.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApproveCategoryUseCaseImplTest {

    @Mock private CategoryRepository categoryRepository;

    private ApproveCategoryUseCase useCase;

    @Test
    void execute_pendingCategory_approvesAndSaves() {
        useCase = new ApproveCategoryUseCaseImpl(categoryRepository);
        Category category = Category.propose(new CategoryId("category-1"), "Gao", "gao", "seller-1", "seller@test.com");
        when(categoryRepository.findById(new CategoryId("category-1"))).thenReturn(Optional.of(category));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = useCase.execute("category-1");

        assertThat(response.status()).isEqualTo(CategoryStatus.APPROVED);
        verify(categoryRepository).save(category);
    }

    @Test
    void execute_notFound_throws() {
        useCase = new ApproveCategoryUseCaseImpl(categoryRepository);
        when(categoryRepository.findById(new CategoryId("missing"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing")).isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void execute_alreadyApproved_throwsIllegalState() {
        useCase = new ApproveCategoryUseCaseImpl(categoryRepository);
        Category category = Category.propose(new CategoryId("category-1"), "Gao", "gao", "seller-1", "seller@test.com");
        category.approve();
        when(categoryRepository.findById(new CategoryId("category-1"))).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> useCase.execute("category-1")).isInstanceOf(IllegalStateException.class);
    }
}
