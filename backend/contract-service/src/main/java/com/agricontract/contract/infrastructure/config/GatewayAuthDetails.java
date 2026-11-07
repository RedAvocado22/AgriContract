package com.agricontract.contract.infrastructure.config;

import java.time.DateTimeException;
import java.time.Instant;

/**
 * Extra caller facts the gateway forwards with a valid gateway secret. authTime = JWT auth_time (when the password
 * was last entered), from X-User-Auth-Time in epoch seconds; null when absent or unreadable.
 */
public record GatewayAuthDetails(Instant authTime) {
    public static final String AUTH_TIME_HEADER = "X-User-Auth-Time";

    static GatewayAuthDetails fromHeader(String authTimeHeader) {
        if (authTimeHeader == null || authTimeHeader.isBlank()) {
            return new GatewayAuthDetails(null);
        }
        try {
            return new GatewayAuthDetails(Instant.ofEpochSecond(Long.parseLong(authTimeHeader.trim())));
        } catch (NumberFormatException | DateTimeException unreadable) {
            return new GatewayAuthDetails(null);
        }
    }
}
