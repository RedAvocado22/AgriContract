package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.NegotiationRevision;

import java.util.List;

public interface NegotiationHistoryRepository {
    List<NegotiationRevision> findByContractId(String contractId);
}
