package com.agricontract.contract.domain.model.terms;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** D38: deposit = rate × total, rounded HALF_UP to the whole đồng, written with two decimals. */
class DepositAmountTest {

    @Test
    void depositsAreRoundedToTheWholeDong() {
        // Fixture rates: buyer 5%, seller 3%. 237,512,345.00 × 5% = 11,875,617.25; × 3% = 7,125,370.35.
        ContractTermsSnapshot terms = withTotal("237512345.00");

        assertThat(terms.buyerDepositAmount().wire()).isEqualTo("11875617.00");
        assertThat(terms.sellerDepositAmount().wire()).isEqualTo("7125370.00");
    }

    @Test
    void halfADongRoundsUp() {
        // 10.00 × 5% = 0.50 → 1.00; 10.00 × 3% = 0.30 → 0.00.
        ContractTermsSnapshot terms = withTotal("10.00");

        assertThat(terms.buyerDepositAmount().wire()).isEqualTo("1.00");
        assertThat(terms.sellerDepositAmount().wire()).isEqualTo("0.00");
    }

    @Test
    void exactAmountsStayExact() {
        // 95,000 đ/kg × 2,500.5 kg = 237,547,500.00.
        ContractTermsSnapshot terms = withTotal("237547500.00");

        assertThat(terms.buyerDepositAmount().wire()).isEqualTo("11877375.00");
        assertThat(terms.sellerDepositAmount().wire()).isEqualTo("7126425.00");
    }

    private static ContractTermsSnapshot withTotal(String total) {
        return new ContractTermsSnapshot(input(), null, goods(), VndAmount.parse(total));
    }
}
