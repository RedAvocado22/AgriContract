package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.domain.model.consent.SignerRole;

import java.util.regex.Pattern;

/** InitiateSignRequest (golden-flow-api.yaml): every field required, unknown fields refused by the reader. */
public record InitiateSignRequest(SignerRole signerRole, String consentStatementVersion, String presentedContentHash) {
    private static final Pattern HASH = Pattern.compile("^[a-f0-9]{64}$");

    public InitiateSignRequest {
        if (signerRole == null) {
            throw new IllegalArgumentException("signerRole is required (BUYER or SELLER)");
        }
        if (consentStatementVersion == null || consentStatementVersion.isEmpty()) {
            throw new IllegalArgumentException("consentStatementVersion is required");
        }
        if (presentedContentHash == null || !HASH.matcher(presentedContentHash).matches()) {
            throw new IllegalArgumentException("presentedContentHash must be 64 lowercase hex characters");
        }
    }
}
