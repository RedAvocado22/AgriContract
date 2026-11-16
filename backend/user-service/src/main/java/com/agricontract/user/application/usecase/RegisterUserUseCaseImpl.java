package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.exception.VerificationEmailNotSentException;
import com.agricontract.user.application.service.EmailVerificationCodeIssuer;
import com.agricontract.user.common.exception.UserAlreadyExistsException;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

// Verifying the email is part of registration (design §4.4): a new profile is stored with the body email unverified
// and the first code is emailed right after it is saved. Registration does not fail when that email does not go out,
// since the profile is already stored; the result says so and the client offers a resend.
// An existing profile is returned as is (200), whatever the body says, and gets no new code.
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {
    private final UserProfileRepository userProfileRepository;
    private final EmailVerificationCodeIssuer codeIssuer;

    @Override
    public RegisterUserResult execute(RegisterUserCommand command) {
        UserId userId = new UserId(command.userId());
        return userProfileRepository.findById(userId)
                .map(existing -> RegisterUserResult.existing(UserProfileResponse.of(existing)))
                .orElseGet(() -> create(command, userId));
    }

    private RegisterUserResult create(RegisterUserCommand command, UserId userId) {
        // An address another user has proven is refused up front (accepted disclosure, design §4.4); unproven
        // duplicates are allowed and the first user to confirm a code owns the address.
        if (userProfileRepository.isEmailVerifiedByAnotherUser(command.email(), userId)) {
            throw new EmailAlreadyClaimedException();
        }
        UserProfile saved;
        try {
            ContactInfo contactInfo = new ContactInfo(command.email(), command.phone(), command.address());
            saved = userProfileRepository.save(
                    UserProfile.create(userId, command.organizationName(), command.role(), contactInfo));
        } catch (UserAlreadyExistsException e) {
            UserProfile existing = userProfileRepository.findById(userId).orElseThrow(() -> e);
            return RegisterUserResult.existing(UserProfileResponse.of(existing));
        }
        // TODO (Sprint 2): call saved.pullDomainEvents() and publish via outbox
        return RegisterUserResult.created(UserProfileResponse.of(saved), sendFirstCode(userId.value()));
    }

    // The code's expiry, or null when no code went out.
    private Instant sendFirstCode(String userId) {
        try {
            return codeIssuer.issue(userId).expiresAt();
        } catch (VerificationEmailNotSentException | EmailAlreadyClaimedException
                 | EmailVerificationConflictException e) {
            // Claimed here means another user confirmed the address after the check above.
            log.info("First verification code not sent for new user {}: {}", userId, e.getClass().getSimpleName());
            return null;
        }
    }
}
