package com.agricontract.escrow.domain.exception;

public class InvalidDepositLegException extends RuntimeException {
    public InvalidDepositLegException(String message) {
        super(message);
    }
}
