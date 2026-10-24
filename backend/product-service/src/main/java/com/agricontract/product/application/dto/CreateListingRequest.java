package com.agricontract.product.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateListingRequest(
        String listingId,
        @NotBlank String productId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotBlank String quantityUnit,
        @DecimalMin("0.001") BigDecimal minOrderQuantity,  // optional; must not exceed quantity
        @NotNull @DecimalMin("0.01") BigDecimal priceFloor,
        @NotBlank String currency,
        @NotNull @FutureOrPresent LocalDate deliveryDeadline,
        // every plot the goods come from, chosen from the seller's registry (design §4, EUDR: no mass balance)
        @NotEmpty List<@NotBlank String> registryEntryIds
) {}
