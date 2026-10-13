package com.agricontract.user.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
        @NotBlank String organizationName,
        @NotBlank @Email String email,
        String phone,
        String address
) {
}
