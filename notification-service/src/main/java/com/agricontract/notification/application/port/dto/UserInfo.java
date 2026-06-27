package com.agricontract.notification.application.port.dto;

public record UserInfo(String userId,
                       String organizationName,
                       String email,
                       String role) {
}
