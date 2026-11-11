package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EmailVerificationSentResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Emails a code to the caller's contact address (design §4.4). The new challenge replaces any pending one. The email
 * goes out inside the transaction: if notification-service does not accept it, the challenge is rolled back and the
 * caller asks again (503); the previous pending code, if any, stays usable.
 */
@Service
@RequiredArgsConstructor
public class RequestEmailVerificationUseCaseImpl implements RequestEmailVerificationUseCase {
    private final UserProfileRepository userProfileRepository;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final VerificationCodes codes;
    private final VerificationEmailSender sender;
    private final EmailVerificationPolicy policy;
    private final Clock clock;

    @Override
    @Transactional
    public EmailVerificationSentResponse execute(String userId) {
        UserProfile profile = userProfileRepository.findById(new UserId(userId))
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
        policy.ensureCanResend(challengeRepository.findByUserId(userId).orElse(null), now);

        String code = codes.newCode();
        EmailVerificationChallenge challenge = EmailVerificationChallenge.issue(userId, email, codes.hash(code), now,
                policy);
        challengeRepository.save(challenge);
        sender.send(UUID.randomUUID().toString(), userId, email, code, challenge.getExpiresAt());
        return new EmailVerificationSentResponse(challenge.getExpiresAt());
    }
}
