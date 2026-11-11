package com.agricontract.user.application.port;

// Generates 6-digit codes and fingerprints them with a server-side secret; the plain code is never stored.
public interface VerificationCodes {
    String newCode();

    String hash(String code);

    boolean matches(String code, String codeHash);
}
