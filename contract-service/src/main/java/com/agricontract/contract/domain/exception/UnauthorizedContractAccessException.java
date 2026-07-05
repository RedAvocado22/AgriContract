package com.agricontract.contract.domain.exception;

public class UnauthorizedContractAccessException extends RuntimeException {
    public UnauthorizedContractAccessException(String message) {
        super(message);
    }
}
