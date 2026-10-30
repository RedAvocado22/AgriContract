package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, Long> {
    @Query("""
            select u.userId from UserProfileJpaEntity u
            where u.role = com.agricontract.user.domain.model.vo.Role.INSPECTOR
              and u.verificationStatus = com.agricontract.user.domain.model.vo.VerificationStatus.VERIFIED
              and (u.authorizationExpiresAt is null or u.authorizationExpiresAt > :now)
            order by u.userId
            """)
    List<String> findEligibleInspectorIds(@Param("now") Instant now);
    Optional<UserProfileJpaEntity> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<UserProfileJpaEntity> findByVerificationStatus(VerificationStatus verificationStatus, Pageable pageable);

    long countByVerificationStatus(VerificationStatus verificationStatus);
}
