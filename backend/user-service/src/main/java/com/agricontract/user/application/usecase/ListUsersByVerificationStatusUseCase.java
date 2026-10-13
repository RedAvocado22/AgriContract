package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.ListUsersByVerificationStatusCommand;
import com.agricontract.user.application.dto.PageResult;

public interface ListUsersByVerificationStatusUseCase {
    PageResult<AdminUserResponse> execute(ListUsersByVerificationStatusCommand command);
}
