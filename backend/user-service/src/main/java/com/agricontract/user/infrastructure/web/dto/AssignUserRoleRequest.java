package com.agricontract.user.infrastructure.web.dto;

import com.agricontract.user.domain.model.vo.Role;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

public record AssignUserRoleRequest(@NotNull Role role) {
    // Contract has additionalProperties: false.
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property: " + name);
    }
}
