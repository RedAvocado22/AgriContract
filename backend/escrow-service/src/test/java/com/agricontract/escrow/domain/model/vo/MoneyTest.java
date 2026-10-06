package com.agricontract.escrow.domain.model.vo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void add_sameCurrency_sumsCorrectly() {
        Money a = new Money(new BigDecimal("100000"), "VND");
        Money b = new Money(new BigDecimal("50000"), "VND");

        Money result = a.add(b);

        assertThat(result).isEqualTo(new Money(new BigDecimal("150000"), "VND"));
    }

    @Test
    void add_differentCurrency_throws() {
        Money vnd = new Money(new BigDecimal("100000"), "VND");
        Money usd = new Money(new BigDecimal("100"), "USD");

        assertThatThrownBy(() -> vnd.add(usd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtract_sameCurrency_subtractsCorrectly() {
        Money a = new Money(new BigDecimal("100000"), "VND");
        Money b = new Money(new BigDecimal("30000"), "VND");

        Money result = a.subtract(b);

        assertThat(result).isEqualTo(new Money(new BigDecimal("70000"), "VND"));
    }

    @Test
    void subtract_differentCurrency_throws() {
        Money vnd = new Money(new BigDecimal("100000"), "VND");
        Money usd = new Money(new BigDecimal("100"), "USD");

        assertThatThrownBy(() -> vnd.subtract(usd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiply_nonTie_roundsNormally() {
        Money amount = new Money(new BigDecimal("100000"), "VND");

        Money result = amount.multiply(new BigDecimal("0.337"));

        assertThat(result).isEqualTo(new Money(new BigDecimal("33700"), "VND"));
    }

    @Test
    void multiply_tieRoundsToEven_downward() {
        Money amount = new Money(new BigDecimal("2"), "VND");

        // 2 * 0.25 = 0.5 -> HALF_EVEN rounds to 0 (even), not 1
        Money result = amount.multiply(new BigDecimal("0.25"));

        assertThat(result).isEqualTo(new Money(new BigDecimal("0"), "VND"));
    }

    @Test
    void multiply_tieRoundsToEven_upward() {
        Money amount = new Money(new BigDecimal("6"), "VND");

        // 6 * 0.25 = 1.5 -> HALF_EVEN rounds to 2 (even), not 1
        Money result = amount.multiply(new BigDecimal("0.25"));

        assertThat(result).isEqualTo(new Money(new BigDecimal("2"), "VND"));
    }
}
