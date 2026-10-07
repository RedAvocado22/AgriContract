package com.agricontract.user.domain.exception;

import com.agricontract.user.domain.model.vo.VerificationStatus;

public class InvalidVerificationTransitionException extends RuntimeException {
    public InvalidVerificationTransitionException(String action, VerificationStatus currentStatus) {
        super("Cannot " + action + " from status: " + currentStatus);
    }
}
