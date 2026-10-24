package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.AddPlotRequest;
import com.agricontract.product.application.dto.RegisteredPlot;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.service.PlotGeometries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddPlotManuallyUseCaseImpl implements AddPlotManuallyUseCase {

    private final PlotRegistrar plotRegistrar;

    @Override
    public RegisteredPlot execute(String sellerId, AddPlotRequest request) {
        return plotRegistrar.register(sellerId, request.householdLabel(),
                PlotGeometries.point(request.longitude(), request.latitude(), request.areaHectares()),
                PlotSource.MANUAL_PIN, request.accuracyMeters(), request.declaredProvince());
    }
}
