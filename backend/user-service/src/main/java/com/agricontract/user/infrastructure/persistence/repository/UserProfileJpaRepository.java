package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // Native because locked_until is not updatable through the entity and last_lock_revision is not mapped at all:
    // only these two statements write them. MySQL evaluates SET left to right, so the CASE still reads the old value.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE user_profiles
               SET locked_until = CASE WHEN locked_until IS NULL OR locked_until < :lockedUntil
                                       THEN :lockedUntil ELSE locked_until END,
                   last_lock_revision = :lockRevision
             WHERE user_id = :userId AND last_lock_revision < :lockRevision
            """, nativeQuery = true)
    int applyLock(@Param("userId") String userId, @Param("lockedUntil") Instant lockedUntil,
                  @Param("lockRevision") long lockRevision);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE user_profiles
               SET locked_until = :effectiveLockedUntil,
                   last_lock_revision = :lockRevision
             WHERE user_id = :userId AND last_lock_revision < :lockRevision
            """, nativeQuery = true)
    int applyUnlock(@Param("userId") String userId, @Param("effectiveLockedUntil") Instant effectiveLockedUntil,
                    @Param("lockRevision") long lockRevision);
}
