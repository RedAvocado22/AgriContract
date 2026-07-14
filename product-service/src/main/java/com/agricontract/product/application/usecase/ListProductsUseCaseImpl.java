package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.domain.repository.ProductRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListProductsUseCaseImpl implements ListProductsUseCase {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public PagedResult<ProductResponse> execute(Pageable pageable) {
        List<ProductResponse> content = productRepository.findAll(pageable)
                .stream()
                .map(productMapper::toResponse)
                .toList();
        long total = productRepository.countAll();
        return new PagedResult<>(content, total, (int) Math.ceil((double) total / pageable.getPageSize()));
    }
}
