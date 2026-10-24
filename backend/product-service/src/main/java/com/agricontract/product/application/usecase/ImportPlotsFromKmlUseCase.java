package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PlotImportResult;
import com.agricontract.product.domain.model.vo.Province;

public interface ImportPlotsFromKmlUseCase {
    PlotImportResult execute(String sellerId, byte[] kml, Province declaredProvince);
}
