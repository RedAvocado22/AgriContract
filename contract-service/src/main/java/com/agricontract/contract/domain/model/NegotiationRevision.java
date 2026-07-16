package com.agricontract.contract.domain.model;

import com.agricontract.contract.domain.model.vo.ContractTerms;

import java.time.Instant;

public record NegotiationRevision(
        int termsRevision,
        String proposedBy,
        Instant proposedAt,
        ContractTerms terms
) {
}
