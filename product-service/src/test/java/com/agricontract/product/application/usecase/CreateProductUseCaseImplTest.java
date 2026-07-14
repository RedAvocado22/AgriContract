package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateProductRequest;
import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.common.exception.CategoryNotApprovedException;
import com.agricontract.product.common.exception.CategoryNotFoundException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;

    private CreateProductUseCase useCase;

    private static final List<String> IMAGES = List.of("https://cdn.test/a.jpg");

    private Category approvedCategory() {
        Category category = Category.propose(new CategoryId("category-1"), "Gao", "gao", "admin", "admin@test.com");
        category.approve();
        return category;
    }

    @Test
    void execute_approvedCategory_createsProduct() {
        useCase = new CreateProductUseCaseImpl(productRepository, categoryRepository);
        when(productRepository.findById(any())).thenReturn(Optional.empty());
        when(categoryRepository.findById(new CategoryId("category-1"))).thenReturn(Optional.of(approvedCategory()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = useCase.execute(
                new CreateProductRequest("product-1", "Gao ST25", "kg", "category-1", IMAGES));

        assertThat(response.categoryId()).isEqualTo("category-1");
        assertThat(response.images()).isEqualTo(IMAGES);
    }

    @Test
    void execute_categoryNotFound_throws() {
        useCase = new CreateProductUseCaseImpl(productRepository, categoryRepository);
        when(productRepository.findById(any())).thenReturn(Optional.empty());
        when(categoryRepository.findById(new CategoryId("missing"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                new CreateProductRequest("product-1", "Gao ST25", "kg", "missing", IMAGES)))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void execute_categoryPending_throwsNotApproved() {
        useCase = new CreateProductUseCaseImpl(productRepository, categoryRepository);
        when(productRepository.findById(any())).thenReturn(Optional.empty());
        Category pending = Category.propose(new CategoryId("category-1"), "Gao", "gao", "admin", "admin@test.com");
        when(categoryRepository.findById(new CategoryId("category-1"))).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.execute(
                new CreateProductRequest("product-1", "Gao ST25", "kg", "category-1", IMAGES)))
                .isInstanceOf(CategoryNotApprovedException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void execute_sameIdSameAttributes_idempotentReturnsExisting() {
        useCase = new CreateProductUseCaseImpl(productRepository, categoryRepository);
        Product existing = Product.create(new ProductId("product-1"), "Gao ST25", "kg", "category-1", IMAGES);
        when(productRepository.findById(new ProductId("product-1"))).thenReturn(Optional.of(existing));

        ProductResponse response = useCase.execute(
                new CreateProductRequest("product-1", "Gao ST25", "kg", "category-1", IMAGES));

        assertThat(response.productId()).isEqualTo("product-1");
        verify(productRepository, never()).save(any());
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void execute_sameIdDifferentAttributes_throwsConflict() {
        useCase = new CreateProductUseCaseImpl(productRepository, categoryRepository);
        Product existing = Product.create(new ProductId("product-1"), "Gao ST25", "kg", "category-1", IMAGES);
        when(productRepository.findById(new ProductId("product-1"))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(
                new CreateProductRequest("product-1", "Gao khac", "kg", "category-1", IMAGES)))
                .isInstanceOf(IllegalStateException.class);
    }
}
