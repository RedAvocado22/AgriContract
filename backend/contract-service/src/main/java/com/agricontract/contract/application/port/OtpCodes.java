package com.agricontract.contract.application.port;

/** Generates 6-digit codes and fingerprints them with a server-side secret; the plain code is never stored. */
public interface OtpCodes {
    String newCode();

    String hash(String code);

    boolean matches(String code, String otpHash);
}
