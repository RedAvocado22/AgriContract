package com.agricontract.reputation.application.dto;
import java.time.Instant;
import java.util.UUID;
public record AppendDisputeOutcomeCommand(UUID milestoneId, UUID contractId, UUID flaggedByUserId,
                                          String resolutionFavors, Instant resolvedAt, UUID sourceEventId) {}
