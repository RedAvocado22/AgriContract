package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.InternalUserInfoResponse;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

// Returns the raw eligibility facts; the listing/offer/sign gate is decided by the calling use case (design §3).
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
                // null until the user proves the address: callers treat a missing email as "cannot notify" (design §4.3).
                .email(profile.verifiedContactEmail())
                .role(profile.getRole())
                .verificationStatus(profile.getVerificationStatus())
                .authorizationExpiresAt(profile.getAuthorizationExpiresAt())
                .lockedUntil(profile.getLockedUntil())
                .build();
    }
}
