package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 403: a listing named a plot that is not in the seller's registry. An unknown id gets the same answer as
 * another seller's id, so the response does not reveal which plots exist.
 */
public class PlotNotOwnedException extends ApiException {
    public PlotNotOwnedException(String registryEntryId) {
        super(HttpStatus.FORBIDDEN, "PLOT_NOT_OWNED", "Plot " + registryEntryId + " is not in your plot registry");
    }
}
