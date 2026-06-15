package com.agricontract.contract.domain.model.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractTerms(
        Quantity quantity,
        Money agreedPrice,
        LocalDate deliveryDeadline,
        BigDecimal penaltyRate,
        String qualitySpec
) {
}
