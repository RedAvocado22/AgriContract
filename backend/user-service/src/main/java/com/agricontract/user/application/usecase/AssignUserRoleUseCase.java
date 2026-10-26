package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;

public interface AssignUserRoleUseCase {
    AdminUserResponse execute(AssignUserRoleCommand command);
}
