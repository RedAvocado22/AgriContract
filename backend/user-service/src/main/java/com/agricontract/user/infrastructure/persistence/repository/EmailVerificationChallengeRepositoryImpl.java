package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
import com.agricontract.user.infrastructure.persistence.mapper.EmailVerificationChallengeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationChallengeRepositoryImpl implements EmailVerificationChallengeRepository {

    private final EmailVerificationChallengeJpaRepository jpaRepository;
    private final EmailVerificationChallengeMapper mapper;

    @Override
    public Optional<EmailVerificationChallenge> findByUserId(String userId) {
        return jpaRepository.findById(userId).map(mapper::toDomain);
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
}
