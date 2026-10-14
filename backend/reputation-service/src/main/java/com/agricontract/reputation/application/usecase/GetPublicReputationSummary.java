package com.agricontract.reputation.application.usecase;
import com.agricontract.reputation.domain.model.LockHistoryEntry;
import com.agricontract.reputation.domain.model.ReputationSummary;
import com.agricontract.reputation.domain.repository.ReputationReadRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GetPublicReputationSummary {
    private final ReputationReadRepository repository;
    private final Clock clock;
    public GetPublicReputationSummary(ReputationReadRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReputationSummary execute(UUID userId) {
        var history = repository.lockHistory(userId, clock.instant());
        return new ReputationSummary(userId, score(repository.countCompleted(userId), history.size()),
                history.stream().anyMatch(h -> h.status() == LockHistoryEntry.Status.ACTIVE), history);
    }
    public static BigDecimal score(long completed, long locks) {
        if (completed < 0 || locks < 0) throw new IllegalArgumentException("Negative fact count");
        BigDecimal positive = BigDecimal.valueOf(completed);
        BigDecimal total = positive.add(BigDecimal.valueOf(locks));
        return total.signum() == 0 ? null : positive.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP);
    }
}
