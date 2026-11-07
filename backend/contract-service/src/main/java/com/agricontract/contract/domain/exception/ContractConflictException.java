package com.agricontract.contract.domain.exception;

/** A command that is valid in itself but not allowed in the contract's current state (HTTP 409). */
public class ContractConflictException extends RuntimeException {
    public static final String TERMS_FROZEN = "CONTRACT_TERMS_FROZEN";
    public static final String NOT_NEGOTIABLE = "CONTRACT_NOT_NEGOTIABLE";
    public static final String LEGACY_TERMS = "CONTRACT_HAS_NO_COFFEE_TERMS";

    private final String code;

    public ContractConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
