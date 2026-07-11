package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateListingUseCaseImplTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ProductRepository productRepository;

    private CreateListingUseCase useCase;

    @Test
    void execute_happyPath_snapshotsProductCoverImage() {
        useCase = new CreateListingUseCaseImpl(listingRepository, productRepository);
        Product product = Product.create(new ProductId("product-1"), "Gao ST25", "kg", "category-1",
                List.of("https://cdn.test/cover.jpg", "https://cdn.test/2.jpg"));
        when(listingRepository.findById(any())).thenReturn(Optional.empty());
        when(productRepository.findById(new ProductId("product-1"))).thenReturn(Optional.of(product));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ListingResponse response = useCase.execute("seller-1", new CreateListingRequest(
                "listing-1", "product-1", new BigDecimal("100"), "kg",
                new BigDecimal("50000"), "VND", LocalDate.now().plusDays(10)));

        assertThat(response.coverImageUrl()).isEqualTo("https://cdn.test/cover.jpg");
    }
}
