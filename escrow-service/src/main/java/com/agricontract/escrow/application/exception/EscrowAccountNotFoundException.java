package com.agricontract.escrow.application.exception;

public class EscrowAccountNotFoundException extends RuntimeException {
    public EscrowAccountNotFoundException(String contractId) {
        super("No escrow account found for contractId: " + contractId);
    }
}
