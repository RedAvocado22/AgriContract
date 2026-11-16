package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
import com.agricontract.user.infrastructure.persistence.mapper.EmailVerificationChallengeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationChallengeRepositoryImpl implements EmailVerificationChallengeRepository {

    private final EmailVerificationChallengeJpaRepository jpaRepository;
    private final EmailVerificationChallengeMapper mapper;

    @Override
    public Optional<EmailVerificationChallenge> findByUserIdForUpdate(String userId) {
        return jpaRepository.findByUserIdForUpdate(userId).map(mapper::toDomain);
    }

    @Override
    public void save(EmailVerificationChallenge challenge) {
        // userId is the primary key, so this replaces the user's previous challenge.
        jpaRepository.saveAndFlush(mapper.toJpaEntity(challenge));
    }

    @Override
    public void deleteByUserId(String userId) {
        jpaRepository.deleteById(userId);
    }

    @Override
    public void expireIfCode(String userId, String codeHash, Instant now) {
        jpaRepository.expireIfCode(userId, codeHash, now);
    }
}
