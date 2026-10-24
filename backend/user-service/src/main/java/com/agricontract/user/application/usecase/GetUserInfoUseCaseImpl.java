package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.PublicUserResponse;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class GetUserInfoUseCaseImpl implements GetUserInfoUseCase {
    private final UserProfileRepository userProfileRepository;

    @Override
    public PublicUserResponse execute(String userId) {
        UserProfile userProfile = userProfileRepository.findById(new UserId(userId)).orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        return toResponse(userProfile);
    }

    private PublicUserResponse toResponse(UserProfile profile) {
        return PublicUserResponse.builder()
                .userId(profile.getUserId().value())
                .organizationName(profile.getOrganizationName())
                .role(profile.getRole())
                .verificationStatus(profile.getVerificationStatus())
                .build();
    }
}

