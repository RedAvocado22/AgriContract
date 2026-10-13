package com.agricontract.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;

// UTC instant from the real authorization document; null only when the document has no expiry.
// Strict on purpose: a misspelled field or a non-date value must fail, not fall through to null (= no expiry).
public record VerifyUserRequest(
        @JsonDeserialize(using = StrictInstantDeserializer.class) Instant authorizationExpiresAt
) {
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property: " + name);
    }
}
