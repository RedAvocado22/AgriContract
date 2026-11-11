package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.UserProfileResponse;

public interface ConfirmEmailVerificationUseCase {
    // userId: the authenticated principal, never from the request.
    UserProfileResponse execute(String userId, String code);
}
