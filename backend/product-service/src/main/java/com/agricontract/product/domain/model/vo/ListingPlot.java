package com.agricontract.product.domain.model.vo;

import java.math.BigDecimal;

/**
 * Copy of a {@code PlotRegistryEntry} taken when the listing is created (design §2.4): a later re-import of the
 * registry entry does not change it.
 */
public record ListingPlot(
        RegistryEntryId sourceRegistryEntryId,
        String householdLabel,
        PlotGeometry geometry,
        VerificationLevel verificationLevel
) {
}
