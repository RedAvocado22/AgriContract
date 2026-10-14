package com.agricontract.reputation.application.dto;
import java.time.Instant;
import java.util.UUID;
public record AppendCompletedContractCommand(UUID contractId, UUID buyerId, UUID sellerId,
                                             Instant settledAt, UUID sourceEventId) {}
