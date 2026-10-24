package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.AddPlotRequest;
import com.agricontract.product.application.dto.RegisteredPlot;

public interface AddPlotManuallyUseCase {
    RegisteredPlot execute(String sellerId, AddPlotRequest request);
}
