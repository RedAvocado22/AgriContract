package com.agricontract.eventing;

public record EventSubject(SubjectType type, String id) {
    public EventSubject {
        if (type == null) {
            throw new IllegalArgumentException("subject.type is required");
        }

        EventEnvelope.requireText(id, "subject.id");
    }
}
