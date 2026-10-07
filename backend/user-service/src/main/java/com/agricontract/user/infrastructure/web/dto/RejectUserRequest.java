package com.agricontract.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectUserRequest(@NotBlank String reason) {
}
