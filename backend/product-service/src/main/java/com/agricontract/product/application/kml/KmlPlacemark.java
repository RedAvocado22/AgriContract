package com.agricontract.product.application.kml;

import java.math.BigDecimal;
import java.util.List;

/**
 * One placemark as read from the file, before geometry validation.
 *
 * @param name          placemark {@code <name>}, used as the household label ("#n" when missing)
 * @param shell         {@code [longitude, latitude]} points: one for a POINT, the outer ring for a POLYGON
 * @param areaHectares  from ExtendedData, null when absent (required for a point)
 * @param error         why the placemark could not be read; null when readable
 */
public record KmlPlacemark(String name, Kind kind, List<double[]> shell, List<List<double[]>> holes,
                           BigDecimal areaHectares, String error) {

    public enum Kind { POINT, POLYGON, REJECTED }

    static KmlPlacemark point(String name, double[] coordinate, BigDecimal areaHectares) {
        return new KmlPlacemark(name, Kind.POINT, List.of(coordinate), List.of(), areaHectares, null);
    }

    static KmlPlacemark polygon(String name, List<double[]> shell, List<List<double[]>> holes, BigDecimal areaHectares) {
        return new KmlPlacemark(name, Kind.POLYGON, shell, holes, areaHectares, null);
    }

    static KmlPlacemark rejected(String name, String error) {
        return new KmlPlacemark(name, Kind.REJECTED, List.of(), List.of(), null, error);
    }
}
