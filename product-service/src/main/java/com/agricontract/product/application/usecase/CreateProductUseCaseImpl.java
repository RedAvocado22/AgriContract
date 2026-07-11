package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateProductRequest;
import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateProductUseCaseImpl implements CreateProductUseCase {

    private final ProductRepository productRepository;

    @Override
    public ProductResponse execute(CreateProductRequest request) {
        ProductId productId = (request.productId() != null && !request.productId().isBlank())
                ? new ProductId(request.productId())
                : new ProductId(UUID.randomUUID().toString());

        Optional<ProductResponse> matched = assertSameRequestOrConflict(productId, request);
        if (matched.isPresent()) {
            return matched.get();
        }

        Product product = Product.create(productId, request.name(), request.unit(), request.category());

        Product saved;
        try {
            saved = productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            return assertSameRequestOrConflict(productId, request).orElseThrow(() -> e);
        }

        return toResponse(saved);
    }

    private Optional<ProductResponse> assertSameRequestOrConflict(ProductId productId, CreateProductRequest request) {
        Product existing = productRepository.findById(productId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        if (existing.getName().equals(request.name()) &&
                existing.getUnit().equals(request.unit()) &&
                Objects.equals(existing.getCategory(), request.category())) {
            return Optional.of(toResponse(existing));
        }
        throw new IllegalStateException("Product " + productId.value() + " already exists with different attributes");
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .productId(product.getProductId().value())
                .name(product.getName())
                .unit(product.getUnit())
                .category(product.getCategory())
                .build();
    }
}
