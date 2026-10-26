package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PlotResponse;
import com.agricontract.product.application.dto.RegisteredPlot;
import com.agricontract.product.common.exception.DuplicatePlotHouseholdException;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Create-or-update of one household plot, shared by KML import and manual pin (design §4): the seller's
 * existing entry with the same household label is updated in place, otherwise a new entry is created.
 * Lookup is always scoped to the seller, so one seller can never touch another seller's plot.
 */
@Component
@RequiredArgsConstructor
class PlotRegistrar {

    private final PlotRegistryRepository plotRegistryRepository;
    private final Clock clock;

    RegisteredPlot register(String sellerId, String householdLabel, PlotGeometry geometry, PlotSource source,
                            BigDecimal accuracyMeters, Province declaredProvince) {
        String label = PlotRegistryEntry.normalizeLabel(householdLabel);
        Instant now = clock.instant();

        Optional<PlotRegistryEntry> existing = plotRegistryRepository.findBySellerIdAndHouseholdLabel(sellerId, label);
        if (existing.isPresent()) {
            return reimport(existing.get(), geometry, source, accuracyMeters, declaredProvince, now);
        }
        PlotRegistryEntry entry = PlotRegistryEntry.register(new RegistryEntryId(UUID.randomUUID().toString()),
                sellerId, label, geometry, source, accuracyMeters, declaredProvince, now);
        try {
            return new RegisteredPlot(PlotResponse.from(plotRegistryRepository.create(entry)), true);
        } catch (DuplicatePlotHouseholdException e) {
            // a concurrent import of the same household inserted first; apply this one on top of it
            PlotRegistryEntry winner = plotRegistryRepository.findBySellerIdAndHouseholdLabel(sellerId, label)
                    .orElseThrow(() -> e);
            return reimport(winner, geometry, source, accuracyMeters, declaredProvince, now);
        }
    }

    private RegisteredPlot reimport(PlotRegistryEntry entry, PlotGeometry geometry, PlotSource source,
                                    BigDecimal accuracyMeters, Province declaredProvince, Instant now) {
        entry.reimport(geometry, source, accuracyMeters, declaredProvince, now);
        return new RegisteredPlot(PlotResponse.from(plotRegistryRepository.update(entry)), false);
    }
}
