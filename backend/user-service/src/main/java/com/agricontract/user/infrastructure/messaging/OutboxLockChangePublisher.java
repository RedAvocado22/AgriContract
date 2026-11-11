package com.agricontract.user.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.application.port.LockChangePublisher;
import com.agricontract.user.domain.model.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * notification.user_lock_changed_requested (notification-commands.yaml). The command continues the reputation
 * event's chain: causationId is that event's eventId (design §5 sourceEventId; the frozen payload has no such field),
 * correlationId is inherited. effectiveUntil is the stored lock after the change, not the incoming value: a shorter
 * new lock does not shorten a longer one, and the email must say how long the user is actually locked. Nothing is
 * published while the contact email is unverified (design §4.4); the lock itself is already applied.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxLockChangePublisher implements LockChangePublisher {

    static final String PRODUCER = "user-service";
    static final String EVENT_TYPE = "notification.user_lock_changed_requested";

    private final OutboxWriter outbox;
    private final Clock clock;

    @Override
    public void lockChanged(UserProfile profile, ApplyLockChangeCommand change) {
        String recipientEmail = profile.verifiedContactEmail();
        if (recipientEmail == null) {
            log.info("Lock change email for user {} skipped: contact email not verified", profile.getUserId().value());
            return;
        }
        Instant effectiveUntil = profile.getLockedUntil();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", profile.getUserId().value());
        payload.put("recipientEmail", recipientEmail);
        payload.put("action", change.action().name());
        payload.put("effectiveUntil", effectiveUntil == null ? null : effectiveUntil.toString());
        payload.put("reasonCode", change.reasonCode());

        outbox.append(new EventEnvelope(UUID.randomUUID(), EVENT_TYPE, 1, Instant.now(clock), PRODUCER,
                profile.getUserId().value(), change.correlationId(), change.sourceEventId(), null, payload));
    }
}
