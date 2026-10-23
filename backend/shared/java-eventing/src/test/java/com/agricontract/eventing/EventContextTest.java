package com.agricontract.eventing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EventContextTest {

    private static EventEnvelope envelope(UUID eventId, String correlationId, String causationId) {
        return new EventEnvelope(eventId, "contract.signed", 1, Instant.parse("2026-10-23T03:00:00Z"),
                "contract-service", "contract-1", correlationId, causationId, null, Map.of());
    }

    @Test
    void root_withCorrelationId_hasNoCausation() {
        EventContext context = EventContext.root("corr-1");

        assertThat(context.correlationId()).isEqualTo("corr-1");
        assertThat(context.causationId()).isNull();
    }

    @Test
    void root_withoutCorrelationId_doesNotFabricateOne() {
        EventContext context = EventContext.root(null);

        assertThat(context.correlationId()).isNull();
        assertThat(context.causationId()).isNull();
    }

    @Test
    void derivedFrom_rootParent_causationIsParentEventIdAndCorrelationCarriesOver() {
        UUID parentId = UUID.randomUUID();

        EventContext context = EventContext.derivedFrom(envelope(parentId, "corr-1", null));

        assertThat(context.correlationId()).isEqualTo("corr-1");
        assertThat(context.causationId()).isEqualTo(parentId.toString());
    }

    @Test
    void derivedFrom_derivedParent_causationIsParentNotGrandparent() {
        UUID parentId = UUID.randomUUID();

        EventContext context = EventContext.derivedFrom(envelope(parentId, "corr-1", "grandparent-id"));

        assertThat(context.causationId()).isEqualTo(parentId.toString());
    }

    @Test
    void derivedFrom_parentWithoutCorrelation_doesNotFabricateOne() {
        EventContext context = EventContext.derivedFrom(envelope(UUID.randomUUID(), null, null));

        assertThat(context.correlationId()).isNull();
    }

    @Test
    void derivedFrom_chainOfThree_eachLinksToItsDirectParent() {
        UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID();

        EventContext forE2 = EventContext.derivedFrom(envelope(e1, "corr-1", null));
        EventContext forE3 = EventContext.derivedFrom(envelope(e2, forE2.correlationId(), forE2.causationId()));

        assertThat(forE2.causationId()).isEqualTo(e1.toString());
        assertThat(forE3.causationId()).isEqualTo(e2.toString());
        assertThat(forE3.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void create_emptyCorrelationId_throws() {
        assertThatThrownBy(() -> new EventContext("", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void create_emptyCausationId_throws() {
        assertThatThrownBy(() -> new EventContext("corr-1", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("causationId");
    }
}
