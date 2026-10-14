package com.agricontract.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;

// UTC instant from the real authorization document. The key is mandatory: a missing key is 400, never an implicit
// unlimited grant; explicit null is the only way to say the document has no expiry (contract VerifyUserRequest).
// Strict on purpose: a misspelled field or a non-date value must fail, not fall through to null (= no expiry).
public record VerifyUserRequest(
        @JsonProperty(required = true)
        @JsonDeserialize(using = StrictInstantDeserializer.class) Instant authorizationExpiresAt
) {
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property: " + name);
    }
}
