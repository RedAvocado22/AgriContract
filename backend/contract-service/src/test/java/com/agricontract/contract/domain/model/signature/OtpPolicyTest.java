package com.agricontract.contract.domain.model.signature;

import com.agricontract.contract.domain.exception.ContractConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpPolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private final OtpPolicy policy = new OtpPolicy(300, 5, 60, 5);

    @Test
    void firstCodeAndCodeAfterTheCooldown_canBeSent() {
        policy.ensureCanSend(null, 0, NOW);
        policy.ensureCanSend(NOW.minusSeconds(60), 4, NOW);
    }

    @Test
    void codeWithinSixtySecondsOfTheLastOne_isRefused() {
        assertThatThrownBy(() -> policy.ensureCanSend(NOW.minusSeconds(59), 1, NOW))
                .isInstanceOf(ContractConflictException.class)
                .extracting("code").isEqualTo(OtpPolicy.RESEND_TOO_SOON);
    }

    @Test
    void sixthCodeWithinAnHour_isRefused() {
        assertThatThrownBy(() -> policy.ensureCanSend(NOW.minusSeconds(600), 5, NOW))
                .isInstanceOf(ContractConflictException.class)
                .extracting("code").isEqualTo(OtpPolicy.RESEND_LIMIT);
    }
}
