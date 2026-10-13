package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.InternalUserInfoResponse;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class GetInternalUserInfoUseCaseImpl implements GetInternalUserInfoUseCase {
    private final UserProfileRepository userProfileRepository;

    @Override
    public InternalUserInfoResponse execute(String userId) {
        UserProfile profile = userProfileRepository.findById(new UserId(userId))
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        return InternalUserInfoResponse.builder()
                .userId(profile.getUserId().value())
                .organizationName(profile.getOrganizationName())
                .email(profile.getContactInfo().email())
                .role(profile.getRole())
                .verificationStatus(profile.getVerificationStatus())
                .build();
    }
}
