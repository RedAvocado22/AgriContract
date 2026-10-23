package com.agricontract.eventing;

public record EventContext(String correlationId, String causationId) {
    public EventContext {
        EventEnvelope.optionalText(correlationId, "correlationId");
        EventEnvelope.optionalText(causationId, "causationId");
    }

    public static EventContext root(String correlationId) {
        return new EventContext(correlationId, null);
    }

    /** correlationId is inherited from the parent; causationId is the parent's own eventId, not its causationId. */
    public static EventContext derivedFrom(EventEnvelope parent) {
        return new EventContext(parent.correlationId(), parent.eventId().toString());
    }
}