package com.agricontract.reputation.application.dto;
import java.util.Objects;
import java.util.UUID;
public record PublicSummaryQuery(UUID userId) {
    public PublicSummaryQuery { Objects.requireNonNull(userId); }
}
