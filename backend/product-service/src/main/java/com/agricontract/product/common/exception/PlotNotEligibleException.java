package com.agricontract.product.common.exception;

/**
 * 400: the plot belongs to the seller but cannot back a listing yet (geo check still PENDING, design §2.2.4).
 */
public class PlotNotEligibleException extends RuntimeException {
    public PlotNotEligibleException(String registryEntryId) {
        super("Plot " + registryEntryId + " is still waiting for its geo check (PENDING); retry once it has a result");
    }
}
