package com.agricontract.product.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contract ListingCreateRequest (CoffeeListingCreate), the body of both createListing and updateListing. Quantities
 * are kilograms; askingUnitPrice is VND/kg as a 2-decimal string, parsed exactly (never through double).
 */
public record CreateListingRequest(
        @NotBlank String productId,
        @NotBlank String productName,
        @NotNull @Pattern(regexp = "KG", message = "must be KG") String unitOfMeasure,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal offeredQuantity,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal minOrderQuantity, // <= offeredQuantity
        @JsonDeserialize(using = JsonStringOnlyDeserializer.class)
        @NotNull @Size(max = 11) @Pattern(regexp = "^[0-9]+\\.[0-9]{2}$",
                message = "must be a decimal with exactly 2 places, e.g. 55000.00") String askingUnitPrice,
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
) {
    // unevaluatedProperties: false in the contract (quantityAvailable, listingId, ... are refused, not ignored)
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property in listing request: " + name);
    }

    public BigDecimal askingUnitPriceValue() {
        return new BigDecimal(askingUnitPrice);
    }
}
