package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class ContractTermsDto {
    private QuantityDto quantity;
    private MoneyDto agreedPrice;
    private LocalDate deliveryDeadline;
    private BigDecimal buyerPenaltyRate;
    private BigDecimal sellerDepositRate;
    private String qualitySpec;
}
