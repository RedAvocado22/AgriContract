package com.agricontract.contract.infrastructure.feign.dto;

public record UserInfo(
        String userId,
        String organizationName,
        String email,
        String role
) {
}