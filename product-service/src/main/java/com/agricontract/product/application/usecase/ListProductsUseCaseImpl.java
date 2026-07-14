package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.domain.repository.ProductRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListProductsUseCaseImpl implements ListProductsUseCase {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public Page<ProductResponse> execute(Pageable pageable) {
        return productRepository.findAll(pageable).map(productMapper::toResponse);
    }
}
