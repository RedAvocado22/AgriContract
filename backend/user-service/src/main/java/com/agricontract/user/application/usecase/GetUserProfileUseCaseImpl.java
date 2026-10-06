package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class GetUserProfileUseCaseImpl implements GetUserProfileUseCase {
    private final UserProfileRepository userProfileRepository;

    @Override
    public UserProfileResponse execute(String userId) {
        UserProfile userProfile = userProfileRepository.findById(new UserId(userId)).orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        return toResponse(userProfile);
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
