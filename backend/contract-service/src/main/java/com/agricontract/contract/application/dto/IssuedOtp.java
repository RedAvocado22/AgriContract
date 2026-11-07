package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.signature.OtpChallenge;

/** The stored challenge plus the plain code, which lives only in memory until it is handed to the email request. */
public record IssuedOtp(OtpChallenge challenge, String code) {
    @Override
    public String toString() {
        return "IssuedOtp[otpId=" + challenge.otpId() + "]";
    }
}
