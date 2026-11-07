package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.GeoRiskCheck;
import com.agricontract.product.domain.model.vo.RegistryEntryId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlotRegistryRepository {

    Optional<PlotRegistryEntry> findBySellerIdAndHouseholdLabel(String sellerId, String householdLabel);

    List<PlotRegistryEntry> findAllById(Collection<RegistryEntryId> registryEntryIds);

    /**
     * Insert only — a concurrent insert of the same (sellerId, householdLabel) violates the unique key
     * and surfaces as DuplicatePlotHouseholdException; any other integrity error is not translated.
     */
    PlotRegistryEntry create(PlotRegistryEntry entry);

    PlotRegistryEntry update(PlotRegistryEntry entry);

    /**
     * Geo check timeout: PENDING → UNVERIFIED for entries last imported before {@code cutoff}. Conditional on the
     * row still being PENDING, so overlapping runs or a late check result change each row at most once.
     *
     * @return number of entries moved
     */
    int markUnverifiedImportedBefore(Instant cutoff);

    /** Oldest PENDING entries first, at most {@code limit}. */
    List<PlotRegistryEntry> findPendingGeoCheck(int limit);

    /**
     * Stores a geo check result and its audit record together. Conditional on the entry still being PENDING with the
     * geometry that was checked, so a result that lost the race with the timeout, a re-import or another instance is
     * dropped and leaves no audit record.
     *
     * @return whether the result was applied
     */
    boolean recordGeoCheck(GeoRiskCheck check);
}
