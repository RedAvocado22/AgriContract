package com.agricontract.contract.domain.model.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.consent.SignerRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OtpChallengeTest {
    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00.123456789Z");

    private static OtpChallenge fresh() {
        return OtpChallenge.issue(UUID.randomUUID(), UUID.randomUUID(), "c-1", SignerRole.SELLER, "seller-1",
                NOW.minusSeconds(30), "a".repeat(64), "b".repeat(64), NOW, 300);
    }

    @Test
    void issuedChallengeLivesForTheExpiryWindow() {
        OtpChallenge c = fresh();

        assertThat(c.sentAt()).isEqualTo(Instant.parse("2026-11-07T10:00:00.123456Z"));
        assertThat(c.expiresAt()).isEqualTo(c.sentAt().plusSeconds(300));
        assertThat(c.attemptCount()).isZero();
        assertThat(c.verifiedAt()).isNull();
        assertThat(c.isUsable(c.expiresAt().minusNanos(1000), 5)).isTrue();
    }

    @Test
    void expiredAtTheExactExpiryInstant() {
        OtpChallenge c = fresh();

        assertThat(c.isUsable(c.expiresAt(), 5)).isFalse();
        assertConflict(c, c.expiresAt(), OtpChallenge.OTP_EXPIRED);
    }

    @Test
    void exhaustedAfterMaxWrongAttempts() {
        OtpChallenge c = fresh();
        OtpChallenge tried = new OtpChallenge(c.otpId(), c.consentId(), c.contractId(), c.signerRole(), c.userId(),
                c.authTime(), c.signedContentHash(), c.otpHash(), c.sentAt(), c.expiresAt(), null, 5);

        assertConflict(tried, c.sentAt(), OtpChallenge.OTP_LOCKED);
    }

    @Test
    void usedChallengeIsRefused() {
        OtpChallenge c = fresh();
        OtpChallenge used = new OtpChallenge(c.otpId(), c.consentId(), c.contractId(), c.signerRole(), c.userId(),
                c.authTime(), c.signedContentHash(), c.otpHash(), c.sentAt(), c.expiresAt(), c.sentAt(), 0);

        assertConflict(used, c.sentAt(), OtpChallenge.OTP_USED);
    }

    @Test
    void policyRejectsNonPositiveValues() {
        assertThatThrownBy(() -> new OtpPolicy(0, 5, 60, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OtpPolicy(300, 0, 60, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertConflict(OtpChallenge c, Instant at, String code) {
        assertThatThrownBy(() -> c.ensureUsable(at, 5))
                .isInstanceOf(ContractConflictException.class)
                .extracting(e -> ((ContractConflictException) e).getCode())
                .isEqualTo(code);
    }
}
