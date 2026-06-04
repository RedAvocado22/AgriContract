package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.UserProfileResponse;

public interface GetUserProfileUseCase {
    UserProfileResponse execute(String userId);
}
