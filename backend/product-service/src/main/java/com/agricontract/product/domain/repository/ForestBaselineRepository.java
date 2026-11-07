package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.vo.ForestBaselineAssessment;
import com.agricontract.product.domain.model.vo.PlotGeometry;

import java.util.Optional;

// The 2020 forest baseline of the active dataset version (design §2.2.4).
public interface ForestBaselineRepository {

    /**
     * @return the overlaps of {@code geometry} with the baseline, or empty when the geometry is not wholly inside the
     * dataset's coverage, so "no data" is never reported as "no forest"
     */
    Optional<ForestBaselineAssessment> assess(PlotGeometry geometry);
}
