package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.UserProfileJpaEntity;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // SELECT ... FOR UPDATE on uk_user_profiles_user_id: a record lock on the user's row, and a locking read returns
    // the latest committed row rather than the transaction's snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserProfileJpaEntity u where u.userId = :userId")
    Optional<UserProfileJpaEntity> findByUserIdForUpdate(@Param("userId") String userId);

    boolean existsByUserId(String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserProfileJpaEntity u where u.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);

    boolean existsByVerifiedEmailAndUserIdNot(String verifiedEmail, String userId);

    List<UserProfileJpaEntity> findByVerificationStatus(VerificationStatus verificationStatus, Pageable pageable);

    long countByVerificationStatus(VerificationStatus verificationStatus);

    // Native because locked_until is not updatable through the entity and last_lock_revision is not mapped at all:
    // only these two statements write them.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE user_profiles
               SET locked_until = :lockedUntil,
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
