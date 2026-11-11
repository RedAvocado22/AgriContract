package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.EmailVerificationChallenge;

import java.util.Optional;

public interface EmailVerificationChallengeRepository {
    Optional<EmailVerificationChallenge> findByUserId(String userId);

    // Insert or replace: a user has at most one pending challenge.
    void save(EmailVerificationChallenge challenge);

    void deleteByUserId(String userId);
}
