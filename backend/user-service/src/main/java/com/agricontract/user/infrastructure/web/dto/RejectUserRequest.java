package com.agricontract.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;

public record RejectUserRequest(@NotBlank String reason) {
    // Contract has additionalProperties: false.
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property: " + name);
    }
}
