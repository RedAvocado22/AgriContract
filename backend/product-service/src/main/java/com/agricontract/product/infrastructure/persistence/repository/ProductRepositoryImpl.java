package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.common.exception.WriteConflictException;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ProductRepository;
import com.agricontract.product.infrastructure.persistence.entity.ProductJpaEntity;
import com.agricontract.product.infrastructure.persistence.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public Product save(Product product) {
        ProductJpaEntity entity = productMapper.toJpaEntity(product);
        productJpaRepository.findByProductId(product.getProductId().value())
                .ifPresent(existing -> entity.setId(existing.getId()));

        try {
            // flush so a constraint violation surfaces here, not at commit
            return productMapper.toDomain(productJpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw new WriteConflictException("Product " + product.getProductId().value() + " could not be written", e);
        }
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        return productJpaRepository.findByProductId(productId.value()).map(productMapper::toDomain);
    }
}
