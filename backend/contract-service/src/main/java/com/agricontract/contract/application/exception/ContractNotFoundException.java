package com.agricontract.contract.application.exception;

public class ContractNotFoundException extends RuntimeException {
    public ContractNotFoundException(String contractId) {
        super("Contract not found: " + contractId);
    }
}
