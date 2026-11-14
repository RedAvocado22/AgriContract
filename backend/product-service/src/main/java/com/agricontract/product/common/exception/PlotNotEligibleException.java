package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 400: the plot belongs to the seller but cannot back a listing yet (geo check still PENDING, design §2.2.4).
 */
public class PlotNotEligibleException extends ApiException {
    public PlotNotEligibleException(String registryEntryId) {
        super(HttpStatus.BAD_REQUEST, "PLOT_NOT_ELIGIBLE", "Plot " + registryEntryId + " is still waiting for its geo check (PENDING); retry once it has a result");
    }
}
