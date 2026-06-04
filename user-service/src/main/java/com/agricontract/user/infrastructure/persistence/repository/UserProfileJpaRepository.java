package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, Long> {
    Optional<UserProfileJpaEntity> findByUserId(String userId);
    boolean existsByUserId(String userId);
}
