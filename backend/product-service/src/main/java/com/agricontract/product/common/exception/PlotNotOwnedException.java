package com.agricontract.product.common.exception;

/**
 * 403: a listing named a plot that is not in the seller's registry. An unknown id gets the same answer as
 * another seller's id, so the response does not reveal which plots exist.
 */
public class PlotNotOwnedException extends RuntimeException {
    public PlotNotOwnedException(String registryEntryId) {
        super("Plot " + registryEntryId + " is not in your plot registry");
    }
}
