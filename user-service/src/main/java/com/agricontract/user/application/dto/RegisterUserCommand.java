package com.agricontract.user.application.dto;

import com.agricontract.user.domain.model.vo.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record RegisterUserCommand(
        @NotBlank String userId,
        @NotBlank String organizationName,
        @NotNull  Role role,
        @Email @NotBlank String email,
        String phone,
        String address
) {}
