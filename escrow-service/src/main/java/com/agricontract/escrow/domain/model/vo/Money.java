package com.agricontract.escrow.domain.model.vo;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {}
