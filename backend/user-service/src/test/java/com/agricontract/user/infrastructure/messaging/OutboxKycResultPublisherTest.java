package com.agricontract.user.infrastructure.messaging;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxKycResultPublisherTest {

    private static final String USER_ID = "7d4c2f1e-3b5a-4c6d-8e9f-0a1b2c3d4e5f";
    private static final String ACTOR_ID = "1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b";
    private static final Instant DECIDED_AT = Instant.parse("2026-10-07T03:00:00.123456Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private OutboxWriter outbox;

    private OutboxKycResultPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxKycResultPublisher(outbox);
    }

    private static UserProfile pendingProfile() {
        return UserProfile.create(new UserId(USER_ID), "HTX Ca Phe", Role.SELLER,
                new ContactInfo("seller@example.com", null, null));
    }

    @Test
    void decided_verified_writesFrozenCommandWithoutReason() throws Exception {
        UserProfile profile = pendingProfile();
        profile.verify(ACTOR_ID, DECIDED_AT, null);

        publisher.decided(profile);

        EventEnvelope envelope = captured();
        JsonNode json = JSON.readTree(new EventEnvelopeCodec().toJson(envelope));
        FrozenSchemas.assertNotificationCommand("userKycResultRequested", json);
        assertThat(envelope.eventType()).isEqualTo("notification.user_kyc_result_requested");
        assertThat(envelope.aggregateId()).isEqualTo(USER_ID);
        assertThat(envelope.correlationId()).isEqualTo(envelope.eventId().toString());
        assertThat(envelope.causationId()).isNull();
        assertThat(envelope.occurredAt()).isEqualTo(DECIDED_AT);
        assertThat(json.get("payload").has("reason")).isFalse();
        assertThat(json.at("/payload/result").asText()).isEqualTo("VERIFIED");
        assertThat(json.at("/payload/recipientEmail").asText()).isEqualTo("seller@example.com");
        assertThat(json.at("/payload/organizationName").asText()).isEqualTo("HTX Ca Phe");
        assertThat(Instant.parse(json.at("/payload/decidedAt").asText())).isEqualTo(DECIDED_AT);
    }

    @Test
    void decided_rejected_writesFrozenCommandWithReason() throws Exception {
        UserProfile profile = pendingProfile();
        profile.reject(ACTOR_ID, DECIDED_AT, "  Blurry ID scan ");

        publisher.decided(profile);

        JsonNode json = JSON.readTree(new EventEnvelopeCodec().toJson(captured()));
        FrozenSchemas.assertNotificationCommand("userKycResultRequested", json);
        assertThat(json.at("/payload/result").asText()).isEqualTo("REJECTED");
        assertThat(json.at("/payload/reason").asText()).isEqualTo("Blurry ID scan");
    }

    @Test
    void decided_eachCallGetsItsOwnEventId() {
        UserProfile profile = pendingProfile();
        profile.verify(ACTOR_ID, DECIDED_AT, null);

        publisher.decided(profile);
        publisher.decided(profile);

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox, times(2)).append(captor.capture());
        assertThat(captor.getAllValues()).extracting(EventEnvelope::eventId).doesNotHaveDuplicates();
    }

    @Test
    void decided_pending_refusesToPublish() {
        assertThatThrownBy(() -> publisher.decided(pendingProfile()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(outbox);
    }

    private EventEnvelope captured() {
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        return captor.getValue();
    }
}
