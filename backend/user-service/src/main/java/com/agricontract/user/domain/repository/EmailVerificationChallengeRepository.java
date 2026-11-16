package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.EmailVerificationChallenge;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationChallengeRepository {
    // Locks the user's challenge row until the caller's transaction ends, so parallel requests and confirms for the
    // same user run one after another and always see the latest attempts_left. Must run inside a transaction.
    Optional<EmailVerificationChallenge> findByUserIdForUpdate(String userId);

    // Insert or replace: a user has at most one pending challenge.
    void save(EmailVerificationChallenge challenge);

    void deleteByUserId(String userId);

    // Ends the challenge whose code could not be sent. Matches on codeHash so a newer challenge is never touched.
    void expireIfCode(String userId, String codeHash, Instant now);
}
