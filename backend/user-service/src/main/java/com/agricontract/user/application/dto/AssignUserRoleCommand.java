package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.Role;

public record AssignUserRoleCommand(
        String targetUserId,
        Role role
) {
}
