package com.agricontract.contract.infrastructure.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HmacOtpCodesTest {
    private final HmacOtpCodes codes = new HmacOtpCodes("test-pepper-0123456789");

    @Test
    void codesAreSixDigitsIncludingLeadingZeros() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String code = codes.newCode();
            assertThat(code).matches("^[0-9]{6}$");
            seen.add(code);
        }
        assertThat(seen.size()).isGreaterThan(1900);
    }

    @Test
    void hashIsKeyedAndNeverTheCode() {
        String hash = codes.hash("012345");

        assertThat(hash).matches("^[a-f0-9]{64}$").doesNotContain("012345");
        assertThat(codes.hash("012345")).isEqualTo(hash);
        assertThat(new HmacOtpCodes("another-pepper-0123456").hash("012345")).isNotEqualTo(hash);
        assertThat(codes.matches("012345", hash)).isTrue();
        assertThat(codes.matches("012346", hash)).isFalse();
    }

    @Test
    void shortOrMissingPepperIsRefused() {
        assertThatThrownBy(() -> new HmacOtpCodes("short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacOtpCodes(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
