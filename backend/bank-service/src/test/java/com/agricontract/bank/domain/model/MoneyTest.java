package com.agricontract.bank.domain.model;

import com.agricontract.bank.domain.exception.InvalidLedgerEntryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.5", "100.50", "100.000"})
    void normalizesToTwoDecimalsWithoutRounding(String raw) {
        BigDecimal amount = new Money(new BigDecimal(raw)).amount();
        assertThat(amount.scale()).isEqualTo(2);
        assertThat(amount).isEqualByComparingTo(raw);
    }

    @Test
    void rejectsAnAmountThatWouldNeedRounding() {
        assertThatThrownBy(() -> new Money(new BigDecimal("100.005")))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("2 decimal places");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01"})
    void rejectsZeroAndNegative(String raw) {
        assertThatThrownBy(() -> new Money(new BigDecimal(raw)))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("strictly positive");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Money(null)).isInstanceOf(InvalidLedgerEntryException.class);
    }

    @Test
    void fitsDecimal18Comma2() {
        new Money(new BigDecimal("9999999999999999.99"));
        assertThatThrownBy(() -> new Money(new BigDecimal("10000000000000000.00")))
                .isInstanceOf(InvalidLedgerEntryException.class).hasMessageContaining("DECIMAL(18,2)");
    }

    @Test
    void sameAmountIsEqualWhateverTheInputScale() {
        assertThat(new Money(new BigDecimal("100.5"))).isEqualTo(new Money(new BigDecimal("100.50")));
    }
}
