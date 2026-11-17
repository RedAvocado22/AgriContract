package com.agricontract.inspection.application.port;

import com.agricontract.inspection.domain.model.WaitingAssignment;
import com.agricontract.inspection.domain.model.WaitingAssignment.Key;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssignmentWaitingRepository {
    /** Current read under the milestone lock, including after a concurrent commit. */
    boolean exists(Key key);
    void insert(WaitingAssignment waiting);
    /** Lock the actual waiting row without waiting for another worker. */
    Optional<WaitingAssignment> lockSkippingLocked(Key key);
    /** Keyset page; a failed row must not starve later rows on every scan. */
    List<Key> pendingAfter(Key after, int limit);
    void attempted(Key key, Instant at, LocalDate lastReminderOn);
    void delete(Key key);
}
