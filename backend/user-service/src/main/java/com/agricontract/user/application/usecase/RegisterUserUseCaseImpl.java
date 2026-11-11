package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.RegisterUserResult;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.common.exception.UserAlreadyExistsException;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// The body email is stored unverified (design §4.4): it receives no notification until the user confirms a code.
// An existing profile is returned as is (200), whatever the body says; only a new profile checks the address.
@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {
    private final UserProfileRepository userProfileRepository;

    @Override
    public RegisterUserResult execute(RegisterUserCommand command) {
        UserId userId = new UserId(command.userId());
        return userProfileRepository.findById(userId)
                .map(existing -> new RegisterUserResult(UserProfileResponse.of(existing), false))
                .orElseGet(() -> {
                    // An address another user has proven is refused up front; unproven duplicates are allowed and
                    // the first user to confirm a code owns the address.
                    if (userProfileRepository.isEmailVerifiedByAnotherUser(command.email(), userId)) {
                        throw new EmailAlreadyClaimedException();
                    }
                    try {
                        ContactInfo contactInfo = new ContactInfo(
                                command.email(), command.phone(), command.address()
                        );
                        UserProfile profile = UserProfile.create(
                                userId,
                                command.organizationName(),
                                command.role(),
                                contactInfo
                        );
                        UserProfile saved = userProfileRepository.save(profile);
                        // TODO (Sprint 2): call saved.pullDomainEvents() and publish via outbox
                        return new RegisterUserResult(UserProfileResponse.of(saved), true);
                    } catch (UserAlreadyExistsException e) {
                        UserProfile existing = userProfileRepository.findById(userId)
                                .orElseThrow(() -> e);
                        return new RegisterUserResult(UserProfileResponse.of(existing), false);
                    }
                });
    }
}
