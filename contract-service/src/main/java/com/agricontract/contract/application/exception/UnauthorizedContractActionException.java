package com.agricontract.contract.application.exception;

public class UnauthorizedContractActionException extends RuntimeException {
    public UnauthorizedContractActionException(String message) {
        super(message);
    }
}
