package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetProductUseCaseImplTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final GetProductUseCaseImpl useCase = new GetProductUseCaseImpl(repository);

    @Test
    void found_returnsTheReadFields() {
        Product product = Product.reconstruct(new ProductId("prod-1"), "Robusta", "kg", "cat-1",
                List.of("https://cdn.example.com/p/1.jpg"), 2026, "2025-2026", List.of("4C"));
        when(repository.findById(new ProductId("prod-1"))).thenReturn(Optional.of(product));

        ProductResponse response = useCase.execute("prod-1");

        assertThat(response).isEqualTo(new ProductResponse("prod-1", "Robusta", "kg", "cat-1",
                List.of("https://cdn.example.com/p/1.jpg"), 2026, "2025-2026", List.of("4C")));
    }

    @Test
    void unknown_isProductNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing"))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
