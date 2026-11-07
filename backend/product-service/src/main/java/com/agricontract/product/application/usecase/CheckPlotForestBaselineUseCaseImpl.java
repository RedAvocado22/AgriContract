package com.agricontract.product.application.usecase;

import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.ForestBaselineAssessment;
import com.agricontract.product.domain.model.vo.GeoRiskCheck;
import com.agricontract.product.domain.repository.ForestBaselineRepository;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Async forest-baseline check of newly registered or re-imported plots (design §2.2.4), run off the seller's request.
 * A plot outside the baseline coverage gets no result and stays PENDING; the timeout job then makes it UNVERIFIED.
 * The risk level is a label only: nothing here rejects, locks or hides a plot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckPlotForestBaselineUseCaseImpl implements CheckPlotForestBaselineUseCase {

    private final PlotRegistryRepository plotRegistryRepository;
    private final ForestBaselineRepository forestBaselineRepository;
    private final Clock clock;

    @Override
    public int checkPending(int batchSize) {
        List<PlotRegistryEntry> batch = plotRegistryRepository.findPendingGeoCheck(batchSize);
        // stamp the whole batch up front: a plot that gets no result (outside coverage, failure, crash mid-batch)
        // goes behind the others on the next run instead of being picked again ahead of newer plots
        plotRegistryRepository.markGeoCheckAttempted(
                batch.stream().map(PlotRegistryEntry::getRegistryEntryId).toList(), clock.instant());
        int recorded = 0;
        for (PlotRegistryEntry entry : batch) {
            try {
                if (check(entry)) {
                    recorded++;
                }
            } catch (RuntimeException e) {
                // one plot must not stop the batch; it stays PENDING and is retried once the queue comes back round
                log.warn("Forest baseline check failed for plot {}", entry.getRegistryEntryId().value(), e);
            }
        }
        return recorded;
    }

    private boolean check(PlotRegistryEntry entry) {
        Optional<ForestBaselineAssessment> assessment = forestBaselineRepository.assess(entry.getGeometry());
        if (assessment.isEmpty()) {
            return false;
        }
        GeoRiskCheck check = entry.recordGeoCheck(assessment.get(), clock.instant());
        if (!plotRegistryRepository.recordGeoCheck(check)) {
            log.debug("Plot {} changed during its forest baseline check; result dropped",
                    check.registryEntryId().value());
            return false;
        }
        log.info("Plot {} forest baseline {}: {} (overlaps {})", check.registryEntryId().value(),
                check.datasetVersion(), check.riskLevel(), check.matchedBaselineAreaIds());
        return true;
    }
}
