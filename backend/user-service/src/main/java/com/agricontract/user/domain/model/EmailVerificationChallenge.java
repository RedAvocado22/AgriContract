package com.agricontract.user.domain.model;

import lombok.Getter;

import java.time.Instant;

// One pending code per user (design §4.4); a new request replaces it. Only the keyed hash of the code is kept.
// email is the address the code went to: a code proves that address only, never a contact email changed since.
@Getter
public class EmailVerificationChallenge {
    private final String userId;
    private final String email;
    private final String codeHash;
    private final Instant sentAt;
    private final Instant expiresAt;
    private int attemptsLeft;

    public EmailVerificationChallenge(String userId, String email, String codeHash, Instant sentAt,
                                      Instant expiresAt, int attemptsLeft) {
        if (userId == null || email == null || codeHash == null || sentAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Email verification challenge fields must not be null");
        }
        this.userId = userId;
        this.email = email;
        this.codeHash = codeHash;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
        this.attemptsLeft = attemptsLeft;
    }

    public static EmailVerificationChallenge issue(String userId, String email, String codeHash, Instant now,
                                                   EmailVerificationPolicy policy) {
        return new EmailVerificationChallenge(userId, email, codeHash, now,
                now.plusSeconds(policy.expirySeconds()), policy.maxAttempts());
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isExhausted() {
        return attemptsLeft <= 0;
    }

    public void recordWrongCode() {
        attemptsLeft = Math.max(0, attemptsLeft - 1);
    }
}
