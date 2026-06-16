package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.ProductRepository;
import com.agricontract.product.infrastructure.persistence.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;
    private final ProductMapper mapper;

    @Override
    public Product save(Product product) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(product)));
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        return jpaRepository.findByProductId(productId.value()).map(mapper::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }
}
