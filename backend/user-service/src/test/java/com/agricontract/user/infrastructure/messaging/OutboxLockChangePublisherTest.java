package com.agricontract.user.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.LockAction;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import com.agricontract.user.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxLockChangePublisherTest {

    private static final String USER_ID = "7d4c2f1e-3b5a-4c6d-8e9f-0a1b2c3d4e5f";
    private static final String SOURCE_EVENT_ID = "0b6f5c3e-9d1a-4e2b-8c7d-6a5b4c3d2e1f";
    private static final String CORRELATION_ID = "5a4b3c2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d";
    private static final Instant NOW = Instant.parse("2026-11-01T05:00:00Z");
    private static final Instant STORED_UNTIL = Instant.parse("2027-01-31T16:59:59.999Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private OutboxWriter outbox;

    private OutboxLockChangePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxLockChangePublisher(outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void lock_reportsTheStoredLockNotTheIncomingOne() throws Exception {
        // Incoming lock is shorter than the one already stored: the email must report the longer one.
        ApplyLockChangeCommand change = command(LockAction.LOCKED, Instant.parse("2026-12-01T00:00:00Z"));

        publisher.lockChanged(profile(STORED_UNTIL), change);

        EventEnvelope envelope = captured();
        FrozenSchemas.assertNotificationCommand("userLockChangedRequested",
                JSON.readTree(new EventEnvelopeCodec().toJson(envelope)));
        assertThat(envelope.eventType()).isEqualTo("notification.user_lock_changed_requested");
        assertThat(envelope.producer()).isEqualTo("user-service");
        assertThat(envelope.aggregateId()).isEqualTo(USER_ID);
        assertThat(envelope.occurredAt()).isEqualTo(NOW);
        assertThat(envelope.causationId()).isEqualTo(SOURCE_EVENT_ID);
        assertThat(envelope.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(envelope.payload())
                .containsEntry("userId", USER_ID)
                .containsEntry("recipientEmail", "seller@example.com")
                .containsEntry("action", "LOCKED")
                .containsEntry("effectiveUntil", STORED_UNTIL.toString())
                .containsEntry("reasonCode", "CONTRACT_BREACH");
    }

    @Test
    void fullUnlock_sendsNullEffectiveUntil() throws Exception {
        publisher.lockChanged(profile(null), command(LockAction.UNLOCKED, null));

        EventEnvelope envelope = captured();
        JsonNode json = JSON.readTree(new EventEnvelopeCodec().toJson(envelope));
        FrozenSchemas.assertNotificationCommand("userLockChangedRequested", json);
        assertThat(json.at("/payload/action").asText()).isEqualTo("UNLOCKED");
        assertThat(json.at("/payload/effectiveUntil").isNull()).isTrue();
    }

    private EventEnvelope captured() {
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        return captor.getValue();
    }

    private static ApplyLockChangeCommand command(LockAction action, Instant until) {
        return new ApplyLockChangeCommand(SOURCE_EVENT_ID, CORRELATION_ID, USER_ID, action, until,
                "CONTRACT_BREACH", 2);
    }

    private static UserProfile profile(Instant lockedUntil) {
        return UserProfile.reconstitute(new UserId(USER_ID), "HTX Ca Phe", Role.SELLER,
                new ContactInfo("seller@example.com", null, null), VerificationStatus.VERIFIED,
                null, null, null, null, lockedUntil);
    }
}
