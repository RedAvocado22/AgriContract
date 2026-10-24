package com.agricontract.product.application.dto;

/**
 * @param created false when an existing entry of the same household was updated in place
 */
public record RegisteredPlot(PlotResponse plot, boolean created) {
}
