package com.agricontract.product.infrastructure.scheduling;

import com.agricontract.product.application.usecase.CheckPlotForestBaselineUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the forest-baseline check outside the plot registration request (design §2.2.4).
@Component
@RequiredArgsConstructor
public class ForestBaselineCheckJob {

    private final CheckPlotForestBaselineUseCase checkPlotForestBaselineUseCase;

    @Value("${plot.forest-baseline.batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${plot.forest-baseline.check-ms:60000}")
    public void checkPendingPlots() {
        checkPlotForestBaselineUseCase.checkPending(batchSize);
    }
}
