package com.agricontract.product.domain.model.vo;

import java.math.BigDecimal;

/**
 * Validated plot geometry, built only by {@code PlotGeometries}.
 *
 * @param wkt          canonical WKT, longitude-latitude order, 6 decimals
 * @param geometryHash SHA-256 hex of {@code wkt}; equal geometries hash equal regardless of ring start or orientation
 */
public record PlotGeometry(GeometryType type, String wkt, BigDecimal areaHectares, String geometryHash) {
}
