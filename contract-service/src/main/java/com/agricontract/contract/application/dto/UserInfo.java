package com.agricontract.contract.application.dto;

public record UserInfo(
        String userId,
        String organizationName,
        String email,
        String role
) {}
