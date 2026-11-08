package com.agricontract.contract.infrastructure.web.dto;

import java.util.UUID;
import java.util.regex.Pattern;

/** VerifyOtpAndSignRequest (golden-flow-api.yaml): both fields required, unknown fields refused by the reader. */
public record VerifyOtpAndSignRequest(UUID otpId, String otpCode) {
    private static final Pattern CODE = Pattern.compile("^[0-9]{6}$");

    public VerifyOtpAndSignRequest {
        if (otpId == null) {
            throw new IllegalArgumentException("otpId is required");
        }
        if (otpCode == null || !CODE.matcher(otpCode).matches()) {
            throw new IllegalArgumentException("otpCode must be 6 digits");
        }
    }

    /** The code must never reach a log line through this record. */
    @Override
    public String toString() {
        return "VerifyOtpAndSignRequest[otpId=" + otpId + "]";
    }
}
