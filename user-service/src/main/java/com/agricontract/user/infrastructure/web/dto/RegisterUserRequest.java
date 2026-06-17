package com.agricontract.user.infrastructure.web.dto;

import com.agricontract.user.domain.model.vo.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public record RegisterUserRequest(
        @NotBlank String organizationName,
        @NotNull Role role,
        String phone,
        String address
) {
}
