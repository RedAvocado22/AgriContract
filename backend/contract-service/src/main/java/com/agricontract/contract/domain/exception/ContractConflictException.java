package com.agricontract.contract.domain.exception;

/** A command that is valid in itself but not allowed in the contract's current state (HTTP 409). */
public class ContractConflictException extends RuntimeException {
    public static final String TERMS_FROZEN = "CONTRACT_TERMS_FROZEN";
    public static final String NOT_NEGOTIABLE = "CONTRACT_NOT_NEGOTIABLE";
    public static final String LEGACY_TERMS = "CONTRACT_HAS_NO_COFFEE_TERMS";
    public static final String NOT_SIGNABLE = "CONTRACT_NOT_SIGNABLE";
    public static final String ALREADY_SIGNED = "CONTRACT_ALREADY_SIGNED_BY_ACTOR";
    public static final String CONSENT_STATEMENT_OUTDATED = "CONSENT_STATEMENT_OUTDATED";
    public static final String PRESENTED_CONTENT_STALE = "PRESENTED_CONTENT_HASH_MISMATCH";
    public static final String TERMS_CHANGED_SINCE_CODE = "OTP_TERMS_CHANGED";

    private final String code;

    public ContractConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
