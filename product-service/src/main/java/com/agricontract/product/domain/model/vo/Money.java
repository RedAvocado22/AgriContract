package com.agricontract.product.domain.model.vo;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {}
