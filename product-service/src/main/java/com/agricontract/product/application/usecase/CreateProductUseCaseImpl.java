package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateProductRequest;
import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateProductUseCaseImpl implements CreateProductUseCase {

    private final ProductRepository productRepository;

    @Override
    public ProductResponse execute(CreateProductRequest request) {
        Product product = Product.create(
                new ProductId(UUID.randomUUID().toString()),
                request.name(),
                request.unit(),
                request.category()
        );

        Product saved = productRepository.save(product);

        return ProductResponse.builder()
                .productId(saved.getProductId().value())
                .name(saved.getName())
                .unit(saved.getUnit())
                .category(saved.getCategory())
                .build();
    }
}
