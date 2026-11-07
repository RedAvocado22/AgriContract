package com.agricontract.contract.application.exception;

/** The caller may not start signing right now (403). code tells the client what to fix. */
public class SignerNotEligibleException extends RuntimeException {
    public static final String STEP_UP_REQUIRED = "STEP_UP_REQUIRED";
    public static final String UNKNOWN_USER = "SIGNER_PROFILE_NOT_FOUND";
    public static final String NOT_VERIFIED = "SIGNER_NOT_VERIFIED";
    public static final String AUTHORIZATION_EXPIRED = "SIGNER_AUTHORIZATION_EXPIRED";
    public static final String LOCKED = "SIGNER_LOCKED";
    public static final String NO_EMAIL = "SIGNER_EMAIL_MISSING";

    private final String code;

    public SignerNotEligibleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
