package com.agricontract.user.infrastructure.verification;

import com.agricontract.user.application.port.VerificationCodes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

// HMAC-SHA256(code, pepper), not a bare hash: with 10^6 codes, anyone reading the table could try them all.
public class HmacVerificationCodes implements VerificationCodes {
    private static final String ALGORITHM = "HmacSHA256";
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public HmacVerificationCodes(String pepper) {
        if (pepper == null || pepper.length() < 16) {
            throw new IllegalArgumentException("user.email-verification.pepper must be at least 16 characters");
        }
        this.key = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public String newCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    @Override
    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    @Override
    public boolean matches(String code, String codeHash) {
        return MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.US_ASCII),
                codeHash.getBytes(StandardCharsets.US_ASCII));
    }
}
