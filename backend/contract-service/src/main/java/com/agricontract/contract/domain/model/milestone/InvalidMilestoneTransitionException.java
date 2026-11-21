package com.agricontract.contract.domain.model.milestone;

/** A milestone change that its current state does not allow. */
public class InvalidMilestoneTransitionException extends RuntimeException {
    public InvalidMilestoneTransitionException(String message) {
        super(message);
    }
}
