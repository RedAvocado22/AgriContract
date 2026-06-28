package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class MoneyDto {
    private BigDecimal amount;
    private String currency;
}
