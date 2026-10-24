package com.agricontract.user.infrastructure.web.dto;

import com.agricontract.user.domain.model.vo.Role;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

// Strict on purpose: only a named Role is accepted, never an enum ordinal (see StrictRoleDeserializer).
public record AssignUserRoleRequest(@NotNull @JsonDeserialize(using = StrictRoleDeserializer.class) Role role) {
    // Contract has additionalProperties: false.
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property: " + name);
    }
}
