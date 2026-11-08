package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.domain.model.consent.SignerRole;

import java.util.regex.Pattern;

/** InitiateActivationCancelRequest (golden-flow-api.yaml, D42): every field required, unknown fields refused. */
public record InitiateActivationCancelRequest(SignerRole requesterRole, String consentStatementVersion,
                                              String presentedContentHash) {
    private static final Pattern HASH = Pattern.compile("^[a-f0-9]{64}$");

    public InitiateActivationCancelRequest {
        if (requesterRole == null) {
            throw new IllegalArgumentException("requesterRole is required (BUYER or SELLER)");
        }
        if (consentStatementVersion == null || consentStatementVersion.isEmpty()) {
            throw new IllegalArgumentException("consentStatementVersion is required");
        }
        if (presentedContentHash == null || !HASH.matcher(presentedContentHash).matches()) {
            throw new IllegalArgumentException("presentedContentHash must be 64 lowercase hex characters");
        }
    }
}
