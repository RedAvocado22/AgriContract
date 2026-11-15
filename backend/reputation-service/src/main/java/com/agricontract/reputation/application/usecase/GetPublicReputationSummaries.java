package com.agricontract.reputation.application.usecase;

import com.agricontract.reputation.application.dto.BatchSummaryQuery;
import com.agricontract.reputation.application.dto.BatchSummaryResult;
import com.agricontract.reputation.domain.repository.ReputationReadRepository;
import com.agricontract.reputation.application.port.TransactionRunner;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class GetPublicReputationSummaries {
    private final ReputationReadRepository repository;
    private final Clock clock;
    private final TransactionRunner transactionRunner;

    public GetPublicReputationSummaries(ReputationReadRepository repository, Clock clock, TransactionRunner transactionRunner) {
        this.repository = repository;
        this.clock = clock;
        this.transactionRunner = transactionRunner;
    }

    public BatchSummaryResult execute(BatchSummaryQuery query) {
        return transactionRunner.inRepeatableReadOnlyTransaction(() -> {
            var now = clock.instant();
            var projections = repository.publicSummaries(query.userIds(), now);
            return new BatchSummaryResult(projections.stream().map(projection -> new BatchSummaryResult.Item(
                    projection.userId(), projection.score(), projection.locked())).toList());
        });
    }
}
