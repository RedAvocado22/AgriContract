package com.agricontract.user.infrastructure.web.dto;

public record RegisterUserRequest(
        String organizationName,
        String role,
        String phone,
        String address
) {}
