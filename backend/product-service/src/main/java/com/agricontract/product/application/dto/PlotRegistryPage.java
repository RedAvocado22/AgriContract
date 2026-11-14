package com.agricontract.product.application.dto;

import java.util.List;

/** listMyPlots response, exactly the contract's PlotRegistryPage (no first/last/empty flags). */
public record PlotRegistryPage(List<PlotRegistryItem> content, int page, int size, long totalElements,
                               int totalPages) {
}
