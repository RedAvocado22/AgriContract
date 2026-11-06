package com.agricontract.reputation.infrastructure.web;

import com.agricontract.reputation.application.dto.BatchSummaryResult;
import java.util.List;

public record ReputationBatchSummaryResponse(List<ReputationBatchSummaryItem> items) {
    public ReputationBatchSummaryResponse {
        items = List.copyOf(items);
    }

    public static ReputationBatchSummaryResponse from(BatchSummaryResult result) {
        return new ReputationBatchSummaryResponse(result.items().stream().map(item ->
                new ReputationBatchSummaryItem(item.userId(), item.score(), item.locked())).toList());
    }
}
