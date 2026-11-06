package com.agricontract.reputation.application.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BatchSummaryQuery(List<UUID> userIds) {
    public BatchSummaryQuery {
        if (userIds == null || userIds.isEmpty() || userIds.size() > 100
                || userIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(userIds).size() != userIds.size()) {
            throw new IllegalArgumentException("Expected 1 to 100 distinct user IDs");
        }
        userIds = List.copyOf(userIds);
    }
}
