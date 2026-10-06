package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.infrastructure.persistence.entity.CategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {
    Optional<CategoryJpaEntity> findByCategoryId(String categoryId);

    Optional<CategoryJpaEntity> findByNormalizedName(String normalizedName);
}
