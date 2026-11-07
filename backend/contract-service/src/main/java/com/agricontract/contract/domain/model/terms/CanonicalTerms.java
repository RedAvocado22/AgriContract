package com.agricontract.contract.domain.model.terms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Terms together with their RFC 8785 canonical JSON and its SHA-256 (signedContentHash).
 * The canonical JSON is what gets stored; the hash is always recomputed from that stored text, never from
 * re-serialising the Java object, so a later class change cannot alter the fingerprint of stored terms.
 */
public record CanonicalTerms(ContractTermsSnapshot snapshot, String canonicalJson, String signedContentHash) {

    public static CanonicalTerms of(ContractTermsSnapshot snapshot, TermsCanonicalizer canonicalizer) {
        String json = canonicalizer.canonicalJson(snapshot);
        return new CanonicalTerms(snapshot, json, sha256(json));
    }

    /** Rebuilds stored terms and refuses them if the stored text no longer matches its stored fingerprint. */
    public static CanonicalTerms restore(String storedJson, String storedHash, TermsCanonicalizer canonicalizer) {
        String actual = sha256(storedJson);
        if (!actual.equals(storedHash)) {
            throw new IllegalStateException("Stored contract terms do not match their signedContentHash");
        }
        return new CanonicalTerms(canonicalizer.parse(storedJson), storedJson, storedHash);
    }

    public static String sha256(String canonicalJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", impossible);
        }
    }
}
