package com.agricontract.contract.domain.model.terms;

/** Port: turns terms into RFC 8785 canonical JSON (ContractTermsSnapshot wire shape) and back. */
public interface TermsCanonicalizer {
    String canonicalJson(ContractTermsSnapshot snapshot);

    ContractTermsSnapshot parse(String canonicalJson);
}
