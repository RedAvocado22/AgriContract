package com.agricontract.product.domain.model.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * What the goods of a listing are: the coffee fields a contract later freezes into its goods snapshot (design §8b/§8c).
 * categoryId, commodity, cropYear, harvestSeason and certifications are copied from the Product and its Category when
 * the listing is created, the same way as productName, so the listing shows exactly what a contract will snapshot.
 * Every field may be null on a legacy listing created before Phase 2.
 *
 * @param certifications              seller's free-text commitments for future goods, not verified certificates
 * @param warehouseLocation           where the goods are stored; not the contract delivery point
 * @param traceabilityEvidencePackageId reference to an evidence package, nullable
 */
public record ListingGoods(
        CategoryId categoryId,
        Commodity commodity,
        Integer cropYear,
        String harvestSeason,
        List<String> certifications,
        String regionText,
        CoffeeQualitySpec declaredQualitySpec,
        String packagingDeclaration,
        String warehouseLocation,
        LocalDate estimatedAvailabilityDate,
        String traceabilityEvidencePackageId
) {
    public ListingGoods {
        certifications = certifications == null ? null : List.copyOf(certifications);
    }

    public static ListingGoods legacy() {
        return new ListingGoods(null, null, null, null, null, null, null, null, null, null, null);
    }
}
