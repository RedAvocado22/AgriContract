package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.EmailVerificationChallenge;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationChallengeRepository {
    // Locking read of the user's challenge row, so it returns the latest committed attempts_left. It does not serialize
    // callers on its own: with no row InnoDB takes only a gap lock, which does not exclude a second caller. Callers
    // lock the profile row first (UserProfileRepository.findByIdForUpdate), always in the order profile -> challenge.
    // Must run inside a transaction.
    Optional<EmailVerificationChallenge> findByUserIdForUpdate(String userId);

    // Insert or replace: a user has at most one pending challenge.
    void save(EmailVerificationChallenge challenge);

    void deleteByUserId(String userId);

    // Ends the challenge whose code could not be sent. Matches on codeHash so a newer challenge is never touched.
    void expireIfCode(String userId, String codeHash, Instant now);
}
