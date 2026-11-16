package com.agricontract.user.application.service;

import com.agricontract.user.application.dto.EmailVerificationSentResponse;
import com.agricontract.user.application.port.TransactionRunner;
import com.agricontract.user.application.port.VerificationCodes;
import com.agricontract.user.application.port.VerificationEmailSender;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.domain.model.EmailVerificationPolicy;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Emails a verification code to the user's contact address (design §4.4); used by register for the first code and by
 * the resend endpoint. The challenge is committed before the email goes out, so the HTTP call to notification-service
 * never holds a transaction open and a sent code is always one that was stored. If the email is not sent, the
 * challenge just stored is expired (only that one, matched by its code hash) and the send error propagates; an
 * expired challenge does not hold the resend cooldown.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationCodeIssuer {
    private final UserProfileRepository userProfileRepository;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final VerificationCodes codes;
    private final VerificationEmailSender sender;
    private final EmailVerificationPolicy policy;
    private final Clock clock;
    private final TransactionRunner transactionRunner;

    public EmailVerificationSentResponse issue(String userId) {
        Issued issued = transactionRunner.inTransaction(() -> store(userId));
        EmailVerificationChallenge challenge = issued.challenge();
        try {
            sender.send(UUID.randomUUID().toString(), userId, challenge.getEmail(), issued.code(),
                    challenge.getExpiresAt());
        } catch (RuntimeException sendFailure) {
            expire(challenge, sendFailure);
            throw sendFailure;
        }
        return new EmailVerificationSentResponse(challenge.getExpiresAt());
    }

    private Issued store(String userId) {
        // The profile row is the per-user lock: two requests at once run one after another, so only one passes the
        // cooldown. The challenge row cannot be the lock, as it does not exist before the first code (users from
        // before V11, or a register whose first code was refused).
        UserProfile profile = userProfileRepository.findByIdForUpdate(new UserId(userId))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        if (profile.isEmailVerified()) {
            throw new EmailVerificationConflictException(EmailVerificationConflictException.ALREADY_VERIFIED,
                    "the contact email is already verified");
        }
        String email = profile.getContactInfo().email();
        // No code to an address someone else has proven: it could never be confirmed, and it would mail a stranger.
        if (userProfileRepository.isEmailVerifiedByAnotherUser(email, profile.getUserId())) {
            throw new EmailAlreadyClaimedException();
        }

        Instant now = Instant.now(clock);
        policy.ensureCanResend(challengeRepository.findByUserIdForUpdate(userId).orElse(null), now);

        String code = codes.newCode();
        EmailVerificationChallenge challenge = EmailVerificationChallenge.issue(userId, email, codes.hash(code), now,
                policy);
        challengeRepository.save(challenge);
        return new Issued(challenge, code);
    }

    private void expire(EmailVerificationChallenge challenge, RuntimeException sendFailure) {
        try {
            transactionRunner.inTransaction(() -> challengeRepository.expireIfCode(challenge.getUserId(),
                    challenge.getCodeHash(), Instant.now(clock)));
        } catch (RuntimeException expireFailure) {
            // The unsent code stays pending until it expires on its own; the user can resend after the cooldown.
            log.warn("Could not expire the unsent verification code of user {}: {}", challenge.getUserId(),
                    expireFailure.getClass().getSimpleName());
            sendFailure.addSuppressed(expireFailure);
        }
    }

    private record Issued(EmailVerificationChallenge challenge, String code) {
    }
}
