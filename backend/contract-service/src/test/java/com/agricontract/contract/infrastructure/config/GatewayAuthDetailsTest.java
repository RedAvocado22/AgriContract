package com.agricontract.contract.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthDetailsTest {

    @Test
    void epochSecondsBecomeTheAuthTime() {
        assertThat(GatewayAuthDetails.fromHeader("1762596000").authTime())
                .isEqualTo(Instant.ofEpochSecond(1762596000L));
    }

    @Test
    void missingOrUnreadableHeader_givesNoAuthTime() {
        assertThat(GatewayAuthDetails.fromHeader(null).authTime()).isNull();
        assertThat(GatewayAuthDetails.fromHeader("").authTime()).isNull();
        assertThat(GatewayAuthDetails.fromHeader("2026-11-08T10:00:00Z").authTime()).isNull();
        assertThat(GatewayAuthDetails.fromHeader("99999999999999999999").authTime()).isNull();
    }
}
