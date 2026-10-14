package com.agricontract.reputation.application.usecase;
import com.agricontract.reputation.domain.model.CompletedContractFact;
import com.agricontract.reputation.domain.model.DisputeOutcomeFact;
import com.agricontract.reputation.domain.repository.FactRepository;
import org.springframework.stereotype.Service;
@Service
public class AppendReputationFact {
    private final FactRepository facts;
    public AppendReputationFact(FactRepository facts) { this.facts = facts; }
    public FactRepository.AppendResult execute(CompletedContractFact fact) { return facts.append(fact); }
    public FactRepository.AppendResult execute(DisputeOutcomeFact fact) { return facts.append(fact); }
}
