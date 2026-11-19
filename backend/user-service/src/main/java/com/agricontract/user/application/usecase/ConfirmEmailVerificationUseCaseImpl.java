package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EmailVerificationConfirmation;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.port.IdentityAccounts;
import com.agricontract.user.application.port.TransactionRunner;
import com.agricontract.user.application.port.VerificationCodes;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Checks the emailed code and marks the contact address verified (design §4.4).
 * <p>
 * The profile row is locked first, then the challenge row, the same order as {@code EmailVerificationCodeIssuer}, so
 * confirms and resends for the same user run one after another without deadlocking: each sees the attempts left by
 * the one before, and a wrong guess always costs an attempt however many are sent at once. A wrong code is
 * returned as {@link EmailVerificationConfirmation.Rejected}, so the counted attempt commits with the transaction.
 * The first user to confirm an address owns it; a later confirm of the same address gets 409 EMAIL_ALREADY_CLAIMED,
 * also when confirms of two different users race (unique key on verified_email).
 * <p>
 * After the commit Keycloak is told once (D59). If it cannot be reached the user still gets the success: the purge
 * job checks this record before deleting anything and sets Keycloak's flag again then.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmEmailVerificationUseCaseImpl implements ConfirmEmailVerificationUseCase {
    private final UserProfileRepository userProfileRepository;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final VerificationCodes codes;
    private final Clock clock;
    private final TransactionRunner transactionRunner;
    private final IdentityAccounts identityAccounts;

    @Override
    public EmailVerificationConfirmation execute(String userId, String code) {
        EmailVerificationConfirmation result = transactionRunner.inTransaction(() -> confirm(userId, code));
        if (result instanceof EmailVerificationConfirmation.Verified) {
            try {
                identityAccounts.markEmailVerified(userId);
            } catch (RuntimeException e) {
                log.warn("event=keycloak_email_verified_not_synced userId={} reason={}", userId,
                        e.getClass().getSimpleName());
            }
        }
        return result;
    }

    private EmailVerificationConfirmation confirm(String userId, String code) {
        // Profile before challenge, as the issuer does: the opposite order would deadlock against a resend. Both are
        // locking reads, so they see a confirm or resend that committed while we waited.
        UserProfile profile = userProfileRepository.findByIdForUpdate(new UserId(userId))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        Optional<EmailVerificationChallenge> pending = challengeRepository.findByUserIdForUpdate(userId);
        if (profile.isEmailVerified()) {
            throw new EmailVerificationConflictException(EmailVerificationConflictException.ALREADY_VERIFIED,
                    "the contact email is already verified");
        }
        EmailVerificationChallenge challenge = pending.orElseThrow(() -> new EmailVerificationConflictException(
                EmailVerificationConflictException.NOT_REQUESTED, "request a verification code first"));

        Instant now = Instant.now(clock);
        // A code sent to an earlier contact address proves nothing about the current one.
        if (challenge.isExpired(now) || !challenge.getEmail().equalsIgnoreCase(profile.getContactInfo().email())) {
            throw new EmailVerificationConflictException(EmailVerificationConflictException.EXPIRED,
                    "the code has expired; request a new one");
        }
        if (challenge.isExhausted()) {
            throw new EmailVerificationConflictException(EmailVerificationConflictException.ATTEMPTS_EXHAUSTED,
                    "too many wrong codes; request a new one");
        }
        if (!codes.matches(code, challenge.getCodeHash())) {
            challenge.recordWrongCode();
            challengeRepository.save(challenge);
            return new EmailVerificationConfirmation.Rejected(challenge.getAttemptsLeft());
        }
        if (userProfileRepository.isEmailVerifiedByAnotherUser(challenge.getEmail(), profile.getUserId())) {
            throw new EmailAlreadyClaimedException();
        }

        profile.markEmailVerified(challenge.getEmail(), now);
        UserProfile saved = userProfileRepository.update(profile);
        challengeRepository.deleteByUserId(userId);
        return new EmailVerificationConfirmation.Verified(UserProfileResponse.of(saved));
    }
}
