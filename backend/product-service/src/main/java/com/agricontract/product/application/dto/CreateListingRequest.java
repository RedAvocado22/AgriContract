package com.agricontract.product.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        @NotEmpty List<@NotBlank String> registryEntryIds,
        // coffee goods (design §8b/§8c); category, commodity and season data come from the product, never the client
        @NotBlank @Size(max = 255) String regionText,
        @NotNull @Valid CoffeeQualitySpecDto declaredQualitySpec,
        @Size(max = 500) String packagingDeclaration,
        @Size(max = 500) String warehouseLocation,
        LocalDate estimatedAvailabilityDate,
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "must be a UUID") String traceabilityEvidencePackageId
) {}
