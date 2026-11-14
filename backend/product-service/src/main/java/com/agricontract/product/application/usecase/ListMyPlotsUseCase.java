package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PlotRegistryPage;
import com.agricontract.product.domain.repository.PageQuery;

public interface ListMyPlotsUseCase {
    PlotRegistryPage execute(String sellerId, PageQuery page);
}
