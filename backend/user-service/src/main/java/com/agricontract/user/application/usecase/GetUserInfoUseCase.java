package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.UserInfoResponse;

public interface GetUserInfoUseCase {
    UserInfoResponse execute(String userId);
}