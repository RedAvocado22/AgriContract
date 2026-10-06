package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdateProductImagesUseCaseImpl implements UpdateProductImagesUseCase {

    private final ProductRepository productRepository;

    @Override
    public ProductResponse execute(String productId, List<String> imageUrls) {
        Product product = productRepository.findById(new ProductId(productId))
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.updateImages(imageUrls);

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .productId(product.getProductId().value())
                .name(product.getName())
                .unit(product.getUnit())
                .categoryId(product.getCategoryId())
                .images(product.getImages())
                .build();
    }
}
