package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {
    private final UserProfileRepository userProfileRepository;

    @Override
    public UserProfileResponse execute(RegisterUserCommand command) {
        if (userProfileRepository.existsById(new UserId(command.userId()))) {
            UserProfile existing = userProfileRepository
                    .findById(new UserId(command.userId()))
                    .get();
            return toResponse(existing);
        }
        ContactInfo contactInfo = new ContactInfo(
                command.email(), command.phone(), command.address()
        );
        UserProfile profile = UserProfile.create(
                new UserId(command.userId()),
                command.organizationName(),
                command.role(),
                contactInfo
        );
        UserProfile saved = userProfileRepository.save(profile);
        return toResponse(saved);

    }

    private UserProfileResponse toResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(profile.getUserId().value())
                .organizationName(profile.getOrganizationName())
                .role(profile.getRole())
                .email(profile.getContactInfo().email())
                .phone(profile.getContactInfo().phone())
                .address(profile.getContactInfo().address())
                .verificationStatus(profile.getVerificationStatus())
                .build();
    }

}
