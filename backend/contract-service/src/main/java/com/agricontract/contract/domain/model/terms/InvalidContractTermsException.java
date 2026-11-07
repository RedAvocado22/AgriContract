package com.agricontract.contract.domain.model.terms;

public class InvalidContractTermsException extends RuntimeException {
    public InvalidContractTermsException(String message) {
        super(message);
    }
}
