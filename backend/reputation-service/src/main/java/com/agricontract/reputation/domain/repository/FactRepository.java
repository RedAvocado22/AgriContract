package com.agricontract.reputation.domain.repository;
import com.agricontract.reputation.domain.model.CompletedContractFact;
import com.agricontract.reputation.domain.model.DisputeOutcomeFact;
public interface FactRepository {
    enum AppendResult { INSERTED, DUPLICATE }
    AppendResult append(CompletedContractFact fact);
    AppendResult append(DisputeOutcomeFact fact);
}
