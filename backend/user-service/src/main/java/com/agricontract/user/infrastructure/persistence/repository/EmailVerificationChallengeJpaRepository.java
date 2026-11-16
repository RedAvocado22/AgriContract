package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.EmailVerificationChallengeJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationChallengeJpaRepository
        extends JpaRepository<EmailVerificationChallengeJpaEntity, String> {

    // SELECT ... FOR UPDATE: a locking read returns the latest committed row, not the transaction's snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM EmailVerificationChallengeJpaEntity c WHERE c.userId = :userId")
    Optional<EmailVerificationChallengeJpaEntity> findByUserIdForUpdate(@Param("userId") String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EmailVerificationChallengeJpaEntity c SET c.expiresAt = :now "
            + "WHERE c.userId = :userId AND c.codeHash = :codeHash")
    int expireIfCode(@Param("userId") String userId, @Param("codeHash") String codeHash, @Param("now") Instant now);
}
