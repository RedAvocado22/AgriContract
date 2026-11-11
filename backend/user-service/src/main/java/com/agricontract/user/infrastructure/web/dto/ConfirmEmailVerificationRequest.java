package com.agricontract.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ConfirmEmailVerificationRequest(
        @NotNull @Pattern(regexp = "^[0-9]{6}$", message = "code must be 6 digits") String code
) {
}
