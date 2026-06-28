package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class QuantityDto {
    private BigDecimal value;
    private String unit;
}
