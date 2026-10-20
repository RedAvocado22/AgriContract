package com.agricontract.product.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ReleaseListingReservationRequest(
        @NotBlank String listingId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal reservedQuantity,
        @NotNull @Pattern(regexp = "CREATE_PERSISTENCE_COMPENSATION") String reason
) {}
