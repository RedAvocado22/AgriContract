package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.NegotiationRevision;
import com.agricontract.contract.domain.model.vo.ContractTerms;

import java.time.Instant;

public record NegotiationHistoryResponse(
        int termsRevision,
        String proposedBy,
        Instant proposedAt,
        ContractTerms terms
) {
    public static NegotiationHistoryResponse from(NegotiationRevision revision) {
        return new NegotiationHistoryResponse(
                revision.termsRevision(),
                revision.proposedBy(),
                revision.proposedAt(),
                revision.terms());
    }
}
