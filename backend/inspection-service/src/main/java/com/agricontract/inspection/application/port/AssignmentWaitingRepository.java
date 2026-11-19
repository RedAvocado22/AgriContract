package com.agricontract.inspection.application.port;

import com.agricontract.inspection.domain.model.WaitingAssignment;
import com.agricontract.inspection.domain.model.WaitingAssignment.Key;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssignmentWaitingRepository {
    /** Insert a transaction-local reservation, or return false if this milestone already waits. */
    boolean tryReserve(WaitingAssignment waiting);
    void insert(WaitingAssignment waiting);
    void started(Key key, Instant at, LocalDate lastReminderOn);
    /** Lock the actual waiting row without waiting for another worker. */
    Optional<WaitingAssignment> lockSkippingLocked(Key key);
    /** Keyset page; a failed row must not starve later rows on every scan. */
    List<Key> pendingAfter(Key after, int limit);
    void attempted(Key key, Instant at, LocalDate lastReminderOn);
    void delete(Key key);
}
