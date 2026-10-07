package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, Long> {
    Optional<UserProfileJpaEntity> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<UserProfileJpaEntity> findByVerificationStatus(VerificationStatus verificationStatus, Pageable pageable);

    long countByVerificationStatus(VerificationStatus verificationStatus);
}
