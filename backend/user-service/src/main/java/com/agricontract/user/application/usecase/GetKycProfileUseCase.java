package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.GetKycProfileCommand;

public interface GetKycProfileUseCase {
    AdminUserResponse execute(GetKycProfileCommand command);
}
