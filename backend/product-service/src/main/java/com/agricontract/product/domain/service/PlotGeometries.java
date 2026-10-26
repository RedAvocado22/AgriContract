package com.agricontract.product.domain.service;

import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Builds a {@link PlotGeometry} or throws {@link IllegalArgumentException} with a reason the seller can act on.
 * Validity (self-intersection) is checked here with JTS because MySQL ST_IsValid() rejects SRID 4326 (design §2.5).
 * Coordinates are {@code [longitude, latitude]} as in KML and GeoJSON.
 */
public final class PlotGeometries {

    // EUDR: a plot over 4 ha must be described by a polygon. A polygon is accepted at any size (design §2.3).
    public static final BigDecimal POINT_MAX_HECTARES = new BigDecimal("4");
    // One household plot is a few hectares; an area this large means wrong coordinates or a whole region drawn.
    // Also keeps area_hectares well inside DECIMAL(10,4).
    public static final BigDecimal POLYGON_MAX_HECTARES = new BigDecimal("10000");

    // Mainland Viet Nam with a small margin; coffee plots never lie offshore.
    private static final double MIN_LAT = 8.0;
    private static final double MAX_LAT = 23.5;
    private static final double MIN_LNG = 102.0;
    private static final double MAX_LNG = 110.0;

    private static final double SCALE = 1_000_000d; // 6 decimals, about 0.1 m
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(SCALE), 4326);
    // WGS84 semi-major axis, the same sphere as common web-map area tools; within about 0.5% of the ellipsoid
    private static final double EARTH_RADIUS_M = 6_378_137d;

    private PlotGeometries() {
    }

    public static PlotGeometry point(double longitude, double latitude, BigDecimal areaHectares) {
        if (areaHectares == null || areaHectares.signum() <= 0) {
            throw new IllegalArgumentException("A point plot needs a positive areaHectares");
        }
        if (areaHectares.compareTo(POINT_MAX_HECTARES) > 0) {
            throw new IllegalArgumentException("A plot over 4 ha must be declared as a POLYGON (KML import), not a point");
        }
        Coordinate coordinate = coordinate(longitude, latitude);
        return of(GeometryType.POINT, FACTORY.createPoint(coordinate), areaHectares);
    }

    public static PlotGeometry polygon(List<double[]> shell, List<List<double[]>> holes) {
        LinearRing outer = ring(shell);
        LinearRing[] inner = holes.stream().map(PlotGeometries::ring).toArray(LinearRing[]::new);
        Polygon polygon = FACTORY.createPolygon(outer, inner);

        TopologyValidationError error = new IsValidOp(polygon).getValidationError();
        if (error != null) {
            throw new IllegalArgumentException("Invalid polygon: " + error.getMessage());
        }

        double squareMeters = Math.abs(sphericalArea(outer));
        for (LinearRing hole : inner) {
            squareMeters -= Math.abs(sphericalArea(hole));
        }
        BigDecimal hectares = BigDecimal.valueOf(squareMeters / 10_000d).setScale(4, RoundingMode.HALF_UP);
        if (hectares.signum() <= 0) {
            throw new IllegalArgumentException("Polygon area rounds to 0 ha");
        }
        if (hectares.compareTo(POLYGON_MAX_HECTARES) > 0) {
            throw new IllegalArgumentException("Polygon area " + hectares.toPlainString() + " ha is above "
                    + POLYGON_MAX_HECTARES + " ha for one household plot; check the coordinates");
        }
        return of(GeometryType.POLYGON, polygon, hectares);
    }

    /**
     * Rewrites WKT read back from MySQL (its own spacing) into the canonical form the hash was taken from,
     * so {@code geometryHash == SHA-256(wkt)} holds for anyone re-checking a stored plot.
     */
    public static String canonicalWkt(String wkt) {
        try {
            return new WKTWriter(2).write(new WKTReader(FACTORY).read(wkt).norm());
        } catch (ParseException e) {
            throw new IllegalStateException("Stored plot geometry is not valid WKT", e);
        }
    }

    private static PlotGeometry of(GeometryType type, Geometry geometry, BigDecimal areaHectares) {
        // norm() fixes ring start and orientation, so the same shape always gives the same WKT and hash
        String wkt = new WKTWriter(2).write(geometry.norm());
        return new PlotGeometry(type, wkt, areaHectares.setScale(4, RoundingMode.HALF_UP), sha256(wkt));
    }

    private static LinearRing ring(List<double[]> points) {
        if (points.size() < 4) {
            throw new IllegalArgumentException("A polygon ring needs at least 4 coordinates (closed)");
        }
        Coordinate[] coordinates = points.stream()
                .map(p -> {
                    if (p.length < 2) {
                        throw new IllegalArgumentException("Each coordinate needs longitude and latitude");
                    }
                    return coordinate(p[0], p[1]);
                })
                .toArray(Coordinate[]::new);
        if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
            throw new IllegalArgumentException("A polygon ring must be closed (first coordinate = last)");
        }
        return FACTORY.createLinearRing(coordinates);
    }

    private static Coordinate coordinate(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || latitude < MIN_LAT || latitude > MAX_LAT || longitude < MIN_LNG || longitude > MAX_LNG) {
            throw new IllegalArgumentException(
                    "Coordinate (" + longitude + ", " + latitude + ") is outside Viet Nam (longitude, latitude order)");
        }
        return new Coordinate(Math.round(longitude * SCALE) / SCALE, Math.round(latitude * SCALE) / SCALE);
    }

    // Spherical excess of a closed ring (Chamberlain & Duquette, "Some Algorithms for Polygons on a Sphere").
    private static double sphericalArea(LinearRing ring) {
        Coordinate[] c = ring.getCoordinates();
        double total = 0;
        for (int i = 0; i < c.length - 1; i++) {
            double lng1 = Math.toRadians(c[i].x);
            double lng2 = Math.toRadians(c[i + 1].x);
            double lat1 = Math.toRadians(c[i].y);
            double lat2 = Math.toRadians(c[i + 1].y);
            total += (lng2 - lng1) * (2 + Math.sin(lat1) + Math.sin(lat2));
        }
        return total * EARTH_RADIUS_M * EARTH_RADIUS_M / 2;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
