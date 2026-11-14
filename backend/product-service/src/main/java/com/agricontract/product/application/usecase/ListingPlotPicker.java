package com.agricontract.product.application.usecase;

import com.agricontract.product.common.exception.PlotNotEligibleException;
import com.agricontract.product.common.exception.PlotNotOwnedException;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copies the registry plots a seller ticks on CreateListing/UpdateListing (design §2.4, §4). Every id must be the
 * seller's own plot and past the PENDING geo check; the listing keeps these copies even if the registry entry is
 * re-imported later.
 */
@Component
@RequiredArgsConstructor
class ListingPlotPicker {

    private final PlotRegistryRepository plotRegistryRepository;

    List<ListingPlot> pick(String sellerId, List<String> registryEntryIds) {
        Set<RegistryEntryId> ids = new LinkedHashSet<>();
        for (String id : registryEntryIds) {
            if (!ids.add(new RegistryEntryId(id))) {
                throw new IllegalArgumentException("Plot " + id + " is listed more than once");
            }
        }
        Map<RegistryEntryId, PlotRegistryEntry> found = new HashMap<>();
        for (PlotRegistryEntry entry : plotRegistryRepository.findAllById(ids)) {
            found.put(entry.getRegistryEntryId(), entry);
        }

        List<ListingPlot> plots = new ArrayList<>(ids.size());
        for (RegistryEntryId id : ids) {
            PlotRegistryEntry entry = found.get(id);
            if (entry == null || !entry.isOwnedBy(sellerId)) {
                throw new PlotNotOwnedException(id.value());
            }
            if (!entry.canBackListing()) {
                throw new PlotNotEligibleException(id.value());
            }
            plots.add(new ListingPlot(entry.getRegistryEntryId(), entry.getHouseholdLabel(), entry.getGeometry(),
                    entry.getVerificationLevel()));
        }
        return plots;
    }
}
