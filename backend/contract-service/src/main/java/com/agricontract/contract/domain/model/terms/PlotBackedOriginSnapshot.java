package com.agricontract.contract.domain.model.terms;

import java.util.List;

public record PlotBackedOriginSnapshot(String regionText, List<PlotRefSnapshot> plotRefs) {
    public PlotBackedOriginSnapshot {
        plotRefs = plotRefs == null ? null : List.copyOf(plotRefs);
    }
}
