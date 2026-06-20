package com.agricontract.escrow.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ArbitrateRequest(
        @NotNull BigDecimal buyerAmount,
        @NotNull BigDecimal sellerAmount,
        @NotBlank String justification
) {
}
