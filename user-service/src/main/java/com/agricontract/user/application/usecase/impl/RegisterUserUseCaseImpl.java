package com.agricontract.user.application.usecase.impl;

import com.agricontract.user.application.dto.RegisterUserCommand;
import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.usecase.RegisterUserUseCase;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public UserProfileResponse execute(RegisterUserCommand cmd) {
        UserId userId = new UserId(cmd.userId());
        if (userProfileRepository.existsById(userId)) {
            return toResponse(userProfileRepository.findById(userId).orElseThrow());
        }

        UserProfile profile = UserProfile.create(
                cmd.userId(), cmd.organizationName(), cmd.role(),
                cmd.email(), cmd.phone(), cmd.address()
        );
        return toResponse(userProfileRepository.save(profile));
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
