package com.agricontract.contract.infrastructure.otp;

import com.agricontract.contract.application.port.OtpCodes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * HMAC-SHA256(code, pepper), not a bare SHA-256: there are only 10^6 codes, so anyone reading the table could try
 * them all in a moment without the secret (signature design §7).
 */
public class HmacOtpCodes implements OtpCodes {
    private static final String ALGORITHM = "HmacSHA256";
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public HmacOtpCodes(String pepper) {
        if (pepper == null || pepper.length() < 16) {
            throw new IllegalArgumentException("contract.otp.pepper must be at least 16 characters");
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
    public boolean matches(String code, String otpHash) {
        return MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.US_ASCII),
                otpHash.getBytes(StandardCharsets.US_ASCII));
    }
}
