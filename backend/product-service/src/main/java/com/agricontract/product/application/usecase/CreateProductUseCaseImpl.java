package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateProductRequest;
import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.common.exception.CategoryNotApprovedException;
import com.agricontract.product.common.exception.CategoryNotFoundException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CategoryStatus;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.CategoryRepository;
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
    private final CategoryRepository categoryRepository;

    @Override
    public ProductResponse execute(CreateProductRequest request) {
        ProductId productId = (request.productId() != null && !request.productId().isBlank())
                ? new ProductId(request.productId())
                : new ProductId(UUID.randomUUID().toString());

        Optional<ProductResponse> matched = assertSameRequestOrConflict(productId, request);
        if (matched.isPresent()) {
            return matched.get();
        }

        assertCategoryApprovedCoffee(request.categoryId());

        Product product = Product.create(productId, request.name(), request.unit(), request.categoryId(),
                request.imageUrls(), request.cropYear(), request.harvestSeason(), request.certifications());

        Product saved;
        try {
            saved = productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            return assertSameRequestOrConflict(productId, request).orElseThrow(() -> e);
        }

        return toResponse(saved);
    }

    // Phase 2 creates coffee products only (design §1, matrix 22); a category approved before commodity existed is out
    private void assertCategoryApprovedCoffee(String categoryId) {
        Category category = categoryRepository.findById(new CategoryId(categoryId))
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (category.getStatus() != CategoryStatus.APPROVED) {
            throw new CategoryNotApprovedException(categoryId);
        }
        if (!category.isApprovedCoffee()) {
            throw new IllegalArgumentException("Category " + categoryId + " is not a COFFEE category; Phase 2 accepts coffee only");
        }
    }

    private Optional<ProductResponse> assertSameRequestOrConflict(ProductId productId, CreateProductRequest request) {
        Product existing = productRepository.findById(productId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        if (existing.getName().equals(request.name()) &&
                existing.getUnit().equals(request.unit()) &&
                Objects.equals(existing.getCategoryId(), request.categoryId()) &&
                Objects.equals(existing.getImages(), request.imageUrls()) &&
                Objects.equals(existing.getCropYear(), request.cropYear()) &&
                Objects.equals(existing.getHarvestSeason(), blankToNull(request.harvestSeason())) &&
                Objects.equals(existing.getCertifications(), request.certifications())) {
            return Optional.of(toResponse(existing));
        }
        throw new IllegalStateException("Product " + productId.value() + " already exists with different attributes");
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .productId(product.getProductId().value())
                .name(product.getName())
                .unit(product.getUnit())
                .categoryId(product.getCategoryId())
                .images(product.getImages())
                .cropYear(product.getCropYear())
                .harvestSeason(product.getHarvestSeason())
                .certifications(product.getCertifications())
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
