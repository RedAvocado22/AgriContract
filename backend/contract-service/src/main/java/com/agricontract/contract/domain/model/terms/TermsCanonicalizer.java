package com.agricontract.contract.domain.model.terms;

/** Port: turns terms into RFC 8785 canonical JSON (ContractTermsSnapshot wire shape) and back. */
public interface TermsCanonicalizer {
    String canonicalJson(ContractTermsSnapshot snapshot);

    ContractTermsSnapshot parse(String canonicalJson);

    /** Canonical JSON of the negotiated part only; used to fingerprint a request for idempotency. */
    String canonicalInputJson(ContractTermsInput input);
}
