package com.agricontract.user.domain.exception;

import com.agricontract.user.domain.model.vo.Role;

public class RoleSeparationViolationException extends RuntimeException {
    public static final String CODE = "ROLE_SEPARATION_VIOLATION";

    public RoleSeparationViolationException(String userId, Role currentRole, Role requestedRole) {
        super(CODE + ": account " + userId + " cannot move from " + currentRole + " to " + requestedRole
                + "; use a different account");
    }
}
