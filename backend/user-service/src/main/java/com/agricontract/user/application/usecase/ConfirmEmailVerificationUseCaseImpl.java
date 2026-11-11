package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.port.VerificationCodes;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.exception.InvalidVerificationCodeException;
import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Checks the emailed code and marks the contact address verified (design §4.4). A wrong code is counted and committed
 * (noRollbackFor), so guessing stops after maxAttempts. The first user to confirm an address owns it; a later confirm
 * of the same address gets 409 EMAIL_ALREADY_CLAIMED, also when two confirms race (unique key on verified_email).
 */
@Service
@RequiredArgsConstructor
public class ConfirmEmailVerificationUseCaseImpl implements ConfirmEmailVerificationUseCase {
    private final UserProfileRepository userProfileRepository;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final VerificationCodes codes;
    private final Clock clock;

    @Override
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public UserProfileResponse execute(String userId, String code) {
        UserProfile profile = userProfileRepository.findById(new UserId(userId))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        if (profile.isEmailVerified()) {
            throw new EmailVerificationConflictException(EmailVerificationConflictException.ALREADY_VERIFIED,
                    "the contact email is already verified");
        }
        EmailVerificationChallenge challenge = challengeRepository.findByUserId(userId)
                .orElseThrow(() -> new EmailVerificationConflictException(
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
            throw new InvalidVerificationCodeException(challenge.getAttemptsLeft());
        }
        if (userProfileRepository.isEmailVerifiedByAnotherUser(challenge.getEmail(), profile.getUserId())) {
            throw new EmailAlreadyClaimedException();
        }

        profile.markEmailVerified(challenge.getEmail(), now);
        UserProfile saved = userProfileRepository.update(profile);
        challengeRepository.deleteByUserId(userId);
        return UserProfileResponse.of(saved);
    }
}
