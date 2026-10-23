package com.agricontract.eventing.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/**
 * Warns when event_outbox rows stay PENDING past the threshold. The poller stops a batch at the first failed row,
 * so one row that can never be sent (typically a missing binding) holds back everything after it; this is how
 * operators find out. Counterpart of node-eventing's checkStale().
 */
@Slf4j
@RequiredArgsConstructor
public class EventOutboxStaleMonitor {

    private final JdbcTemplate jdbcTemplate;
    private final Duration staleThreshold;

    @Scheduled(fixedDelayString = "${agricontract.eventing.outbox.stale-check-interval:PT1M}")
    public void check() {
        int stale = countStale();
        if (stale > 0) {
            String oldestError = jdbcTemplate.query(
                    "SELECT last_error FROM event_outbox WHERE status = 'PENDING' ORDER BY id LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null);
            log.warn("{} outbox event(s) PENDING longer than {}; oldest last_error: {}", stale, staleThreshold,
                    oldestError);
        }
    }

    public int countStale() {
        Integer stale = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE status = 'PENDING' AND created_at < NOW(3) - INTERVAL ? SECOND",
                Integer.class, staleThreshold.toSeconds());
        return stale == null ? 0 : stale;
    }
}
