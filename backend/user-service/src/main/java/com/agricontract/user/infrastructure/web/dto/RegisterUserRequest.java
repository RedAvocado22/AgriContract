package com.agricontract.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
        @NotBlank String organizationName,
        String phone,
        String address
) {
}
