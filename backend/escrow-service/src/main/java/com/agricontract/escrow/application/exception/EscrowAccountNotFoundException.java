package com.agricontract.escrow.application.exception;

public class EscrowAccountNotFoundException extends RuntimeException {
    public EscrowAccountNotFoundException(String contractId) {
        super("No escrow account found for contractId: " + contractId);
    }

    private EscrowAccountNotFoundException(String fieldName, String value) {
        super("No escrow account found for " + fieldName + ": " + value);
    }

    public static EscrowAccountNotFoundException forEscrowId(String escrowId) {
        return new EscrowAccountNotFoundException("escrowId", escrowId);
    }
}
