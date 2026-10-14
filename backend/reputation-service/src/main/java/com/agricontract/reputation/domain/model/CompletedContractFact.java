package com.agricontract.reputation.domain.model;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record CompletedContractFact(UUID contractId, UUID buyerId, UUID sellerId, Instant settledAt, UUID sourceEventId) {
    public CompletedContractFact {
        Objects.requireNonNull(contractId); Objects.requireNonNull(buyerId);
        Objects.requireNonNull(sellerId); Objects.requireNonNull(sourceEventId);
        settledAt = FactValues.timestamp(settledAt);
    }
    public boolean sameContent(CompletedContractFact other) {
        return contractId.equals(other.contractId) && buyerId.equals(other.buyerId)
                && sellerId.equals(other.sellerId) && settledAt.equals(other.settledAt);
    }
}
