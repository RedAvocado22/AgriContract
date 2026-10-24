package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.PublicUserResponse;

public interface GetUserInfoUseCase {
    PublicUserResponse execute(String userId);
}