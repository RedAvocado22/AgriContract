package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Design §4: public read. A non-UUID id cannot name a product, so it is the same 404 as an unknown one.
@Service
@RequiredArgsConstructor
public class GetProductUseCaseImpl implements GetProductUseCase {

    private final ProductRepository productRepository;

    @Override
    public ProductResponse execute(String productId) {
        return productRepository.findById(new ProductId(productId))
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
