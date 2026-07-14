package com.agricontract.contract.domain.model.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record ContractTerms(
        Quantity quantity,
        Money agreedPrice,
        LocalDate deliveryDeadline,
        BigDecimal buyerPenaltyRate,
        BigDecimal sellerDepositRate,
        String qualitySpec
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContractTerms other)) return false;
        return bigDecimalEquals(this.buyerPenaltyRate, other.buyerPenaltyRate)
                && bigDecimalEquals(this.sellerDepositRate, other.sellerDepositRate)
                && this.quantity.equals(other.quantity)
                && this.agreedPrice.equals(other.agreedPrice)
                && this.deliveryDeadline.equals(other.deliveryDeadline)
                && Objects.equals(this.qualitySpec, other.qualitySpec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                normalize(buyerPenaltyRate),
                normalize(sellerDepositRate),
                deliveryDeadline,
                qualitySpec,
                quantity,
                agreedPrice
        );
    }

    private static boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
