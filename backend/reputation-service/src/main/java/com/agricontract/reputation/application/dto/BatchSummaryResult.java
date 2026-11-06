package com.agricontract.reputation.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BatchSummaryResult(List<Item> items) {
    public BatchSummaryResult {
        items = List.copyOf(items);
    }

    public record Item(UUID userId, BigDecimal score, boolean locked) {}
}
