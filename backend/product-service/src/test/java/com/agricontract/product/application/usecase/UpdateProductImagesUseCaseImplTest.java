package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
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
class UpdateProductImagesUseCaseImplTest {

    @Mock private ProductRepository productRepository;

    private UpdateProductImagesUseCase useCase;

    @Test
    void execute_existingProduct_fullyReplacesImages() {
        useCase = new UpdateProductImagesUseCaseImpl(productRepository);
        Product product = Product.create(new ProductId("product-1"), "Gao ST25", "kg", "category-1",
                List.of("https://cdn.test/a.jpg"));
        when(productRepository.findById(new ProductId("product-1"))).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = useCase.execute("product-1", List.of("https://cdn.test/x.jpg", "https://cdn.test/y.jpg"));

        assertThat(response.images()).containsExactly("https://cdn.test/x.jpg", "https://cdn.test/y.jpg");
        verify(productRepository).save(product);
    }

    @Test
    void execute_notFound_throws() {
        useCase = new UpdateProductImagesUseCaseImpl(productRepository);
        when(productRepository.findById(new ProductId("missing"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing", List.of("https://cdn.test/a.jpg")))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void execute_invalidImages_throwsAndDoesNotSave() {
        useCase = new UpdateProductImagesUseCaseImpl(productRepository);
        Product product = Product.create(new ProductId("product-1"), "Gao ST25", "kg", "category-1",
                List.of("https://cdn.test/a.jpg"));
        when(productRepository.findById(new ProductId("product-1"))).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> useCase.execute("product-1", List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }
}
