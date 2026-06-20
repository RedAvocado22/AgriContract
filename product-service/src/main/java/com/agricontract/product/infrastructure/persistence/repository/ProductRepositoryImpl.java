package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ProductRepository;
import com.agricontract.product.infrastructure.persistence.entity.ProductJpaEntity;
import com.agricontract.product.infrastructure.persistence.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final ProductMapper productMapper;

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = productMapper.toJpaEntity(product);
        ProductJpaEntity savedEntity = productJpaRepository.save(entity);
        return productMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        return productJpaRepository.findByProductId(productId.value()).map(productMapper::toDomain);
    }

    @Override
    public Page<Product> findAll(Pageable pageable) {
        return productJpaRepository.findAll(pageable).map(productMapper::toDomain);
    }
}
