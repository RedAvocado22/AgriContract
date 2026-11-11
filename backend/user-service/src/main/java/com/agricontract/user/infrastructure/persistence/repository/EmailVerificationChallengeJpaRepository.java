package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.infrastructure.persistence.entity.EmailVerificationChallengeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationChallengeJpaRepository
        extends JpaRepository<EmailVerificationChallengeJpaEntity, String> {
}
