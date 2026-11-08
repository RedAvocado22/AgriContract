package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.exception.InvalidDepositLegException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * depositLockRetryMaxAttempts with its backoff (milestone-escrow design §3.1, §8: 3 retries after 5m / 30m / 2h).
 * The n-th failure waits backoff[n-1] before the retry; a failure after the last retry gets no retry.
 */
public record RetrySchedule(List<Duration> backoff) {

    public RetrySchedule {
        if (backoff == null || backoff.isEmpty()
                || backoff.stream().anyMatch(d -> d == null || d.isNegative() || d.isZero())) {
            throw new InvalidDepositLegException("retry backoff must be a non-empty list of positive durations");
        }
        backoff = List.copyOf(backoff);
    }

    /** When to resend after this many failures, or empty when the retries are used up. */
    public Optional<Instant> nextAttempt(int failures, Instant failedAt) {
        if (failures < 1 || failures > backoff.size()) {
            return Optional.empty();
        }
        return Optional.of(failedAt.plus(backoff.get(failures - 1)));
    }
}
