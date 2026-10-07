package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.RejectUserCommand;

public interface RejectUserUseCase {
    AdminUserResponse execute(RejectUserCommand command);
}
