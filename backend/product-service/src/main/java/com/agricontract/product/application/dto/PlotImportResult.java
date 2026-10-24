package com.agricontract.product.application.dto;

import java.util.List;

/**
 * One KML import: a bad placemark is reported by name and skipped, it does not fail the whole file (design §4).
 *
 * @param created  entries new to the registry
 * @param updated  entries matched by household label and updated in place
 * @param rejected placemarks that were not stored, with the reason
 */
public record PlotImportResult(List<PlotResponse> created, List<PlotResponse> updated, List<Rejection> rejected) {

    public record Rejection(String placemark, String reason) {
    }
}
