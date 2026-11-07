package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.consent.ConsentEvidence;

/** Append-only: there is deliberately no update or delete. */
public interface ConsentEvidenceRepository {
    void append(ConsentEvidence evidence);
}
