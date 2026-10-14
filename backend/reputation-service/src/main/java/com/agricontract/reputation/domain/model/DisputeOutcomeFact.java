package com.agricontract.reputation.domain.model;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record DisputeOutcomeFact(UUID milestoneId, UUID contractId, UUID flaggedByUserId,
                                 ResolutionFavors resolutionFavors, Instant resolvedAt, UUID sourceEventId) {
    public DisputeOutcomeFact {
        Objects.requireNonNull(milestoneId); Objects.requireNonNull(contractId);
        Objects.requireNonNull(flaggedByUserId); Objects.requireNonNull(resolutionFavors);
        Objects.requireNonNull(sourceEventId);
        resolvedAt = FactValues.timestamp(resolvedAt);
    }
    public boolean sameContent(DisputeOutcomeFact other) {
        return milestoneId.equals(other.milestoneId) && contractId.equals(other.contractId)
                && flaggedByUserId.equals(other.flaggedByUserId) && resolutionFavors == other.resolutionFavors
                && resolvedAt.equals(other.resolvedAt);
    }
}
