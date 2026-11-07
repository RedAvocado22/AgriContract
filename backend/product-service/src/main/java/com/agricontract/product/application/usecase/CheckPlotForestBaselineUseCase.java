package com.agricontract.product.application.usecase;

public interface CheckPlotForestBaselineUseCase {

    /**
     * Checks up to {@code batchSize} PENDING plots against the 2020 forest baseline.
     *
     * @return number of plots that received a result
     */
    int checkPending(int batchSize);
}
