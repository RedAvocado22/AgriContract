package com.agricontract.product.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateListingRequest(
        @NotBlank String productId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotBlank String quantityUnit,
        @NotNull @DecimalMin("0.01") BigDecimal priceFloor,
        @NotBlank String currency,
        @NotNull @FutureOrPresent LocalDate deliveryDeadline
) {}
