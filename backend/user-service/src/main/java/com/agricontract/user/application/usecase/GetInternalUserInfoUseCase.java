package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.InternalUserInfoResponse;

public interface GetInternalUserInfoUseCase {
    InternalUserInfoResponse execute(String userId);
}
