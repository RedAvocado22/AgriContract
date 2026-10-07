package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.VerifyUserCommand;

public interface VerifyUserUseCase {
    AdminUserResponse execute(VerifyUserCommand command);
}
