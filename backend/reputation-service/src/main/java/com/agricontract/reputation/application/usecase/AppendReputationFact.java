package com.agricontract.reputation.application.usecase;
import com.agricontract.reputation.domain.model.CompletedContractFact;
import com.agricontract.reputation.domain.model.DisputeOutcomeFact;
import com.agricontract.reputation.domain.model.ResolutionFavors;
import com.agricontract.reputation.application.dto.*;
import com.agricontract.reputation.domain.repository.FactRepository;
import org.springframework.stereotype.Service;
@Service
public class AppendReputationFact {
    private final FactRepository facts;
    public AppendReputationFact(FactRepository facts) { this.facts = facts; }
    public AppendFactResult execute(AppendCompletedContractCommand command) {
        return result(facts.append(new CompletedContractFact(command.contractId(), command.buyerId(),
                command.sellerId(), command.settledAt(), command.sourceEventId())));
    }
    public AppendFactResult execute(AppendDisputeOutcomeCommand command) {
        return result(facts.append(new DisputeOutcomeFact(command.milestoneId(), command.contractId(),
                command.flaggedByUserId(), ResolutionFavors.valueOf(command.resolutionFavors()),
                command.resolvedAt(), command.sourceEventId())));
    }
    private static AppendFactResult result(FactRepository.AppendResult result) {
        return new AppendFactResult(switch (result) {
            case INSERTED -> AppendFactResult.Status.INSERTED;
            case DUPLICATE -> AppendFactResult.Status.DUPLICATE;
        });
    }
}
