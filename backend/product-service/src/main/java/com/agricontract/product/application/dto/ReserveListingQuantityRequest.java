package com.agricontract.product.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReserveListingQuantityRequest(
        @NotBlank String contractId,
        @NotBlank String listingId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal requestedQuantity,
        @NotBlank String listingVersionToken
) {}
