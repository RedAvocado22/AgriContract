package com.agricontract.escrow.application.exception;

public class UnauthorizedEscrowActionException extends RuntimeException {
    public UnauthorizedEscrowActionException(String userId, String contractId) {
        super("User " + userId + " is not authorized to act on escrow for contract " + contractId);
    }
}
