package com.agricontract.reputation.application.usecase;
import com.agricontract.reputation.application.dto.PublicSummaryQuery;
import com.agricontract.reputation.application.dto.PublicSummaryResult;
import com.agricontract.reputation.application.port.TransactionRunner;
import com.agricontract.reputation.domain.model.ReputationSummary;
import com.agricontract.reputation.domain.repository.ReputationReadRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
@Service
public class GetPublicReputationSummary {
    private final ReputationReadRepository repository;
    private final Clock clock;
    private final TransactionRunner transactionRunner;
    public GetPublicReputationSummary(ReputationReadRepository repository, Clock clock, TransactionRunner transactionRunner) {
        this.repository = repository; this.clock = clock;
        this.transactionRunner = transactionRunner;
    }
    public PublicSummaryResult execute(PublicSummaryQuery query) {
        return transactionRunner.inRepeatableReadOnlyTransaction(() -> {
            UUID userId = query.userId();
            var now = clock.instant();
            var projection = repository.publicSummaries(List.of(userId), now).getFirst();
            var history = repository.lockHistory(userId, now);
            var summary = new ReputationSummary(userId, projection.score(), projection.locked(), history);
            return new PublicSummaryResult(summary.userId(), summary.score(), summary.locked(),
                    summary.lockHistory().stream().map(lock -> new PublicSummaryResult.LockResult(
                            lock.lockedAt(), lock.lockedUntil(), lock.lockDurationDays(),
                            lock.penalizedRole().name(), lock.status().name())).toList());
        });
    }
}
