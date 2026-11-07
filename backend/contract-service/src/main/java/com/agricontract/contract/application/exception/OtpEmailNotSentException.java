package com.agricontract.contract.application.exception;

/** The OTP email was not confirmed as sent. code is notification-service's ErrorEnvelope.code, or a local one. */
public class OtpEmailNotSentException extends RuntimeException {
    public static final String TIMEOUT = "OTP_EMAIL_TIMEOUT";
    public static final String UNREACHABLE = "NOTIFICATION_UNREACHABLE";
    public static final String UNKNOWN = "OTP_EMAIL_FAILED";

    private final String code;

    public OtpEmailNotSentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
