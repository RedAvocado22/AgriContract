package com.agricontract.reputation.application.usecase;

import com.agricontract.reputation.application.dto.BatchSummaryQuery;
import com.agricontract.reputation.application.dto.BatchSummaryResult;
import com.agricontract.reputation.domain.repository.ReputationReadRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPublicReputationSummaries {
    private final ReputationReadRepository repository;
    private final Clock clock;

    public GetPublicReputationSummaries(ReputationReadRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BatchSummaryResult execute(BatchSummaryQuery query) {
        var now = clock.instant();
        var projections = repository.publicSummaries(query.userIds(), now);
        return new BatchSummaryResult(projections.stream().map(projection -> new BatchSummaryResult.Item(
                projection.userId(), projection.score(), projection.locked())).toList());
    }
}
