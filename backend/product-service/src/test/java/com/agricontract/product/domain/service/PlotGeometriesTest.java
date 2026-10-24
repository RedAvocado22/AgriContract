package com.agricontract.product.domain.service;

import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PlotGeometriesTest {

    // ~108.6 m x 111.3 m square at Buon Ma Thuot latitude, about 1.21 ha
    private static List<double[]> square(double lng, double lat, double side) {
        return List.of(new double[]{lng, lat}, new double[]{lng + side, lat}, new double[]{lng + side, lat + side},
                new double[]{lng, lat + side}, new double[]{lng, lat});
    }

    @Test
    void point_roundsToSixDecimalsAndHashesTheCanonicalWkt() {
        PlotGeometry point = PlotGeometries.point(108.0501234567, 12.6667891234, new BigDecimal("1.5"));

        assertThat(point.type()).isEqualTo(GeometryType.POINT);
        assertThat(point.wkt()).isEqualTo("POINT (108.050123 12.666789)");
        assertThat(point.areaHectares()).isEqualByComparingTo("1.5000");
        assertThat(point.geometryHash()).hasSize(64).matches("[a-f0-9]{64}");
        assertThat(PlotGeometries.point(108.050123, 12.666789, new BigDecimal("1.5")).geometryHash())
                .isEqualTo(point.geometryHash());
    }

    @Test
    void point_over4Hectares_mustBeAPolygon() {
        assertThatThrownBy(() -> PlotGeometries.point(108.05, 12.66, new BigDecimal("4.01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("POLYGON");
        assertThat(PlotGeometries.point(108.05, 12.66, new BigDecimal("4")).type()).isEqualTo(GeometryType.POINT);
    }

    @Test
    void point_withoutPositiveArea_isRejected() {
        assertThatThrownBy(() -> PlotGeometries.point(108.05, 12.66, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlotGeometries.point(108.05, 12.66, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coordinateOutsideVietNam_isRejected_includingSwappedLatLng() {
        // latitude and longitude swapped: (12.66, 108.05) is the classic mistake
        assertThatThrownBy(() -> PlotGeometries.point(12.66, 108.05, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside Viet Nam");
        assertThatThrownBy(() -> PlotGeometries.point(Double.NaN, 12.66, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void polygon_areaIsComputedOnTheSphere() {
        PlotGeometry small = PlotGeometries.polygon(square(108.05, 12.66, 0.001), List.of());
        PlotGeometry large = PlotGeometries.polygon(square(108.05, 12.66, 0.003), List.of());

        assertThat(small.type()).isEqualTo(GeometryType.POLYGON);
        assertThat(small.areaHectares()).isBetween(new BigDecimal("1.18"), new BigDecimal("1.24"));
        assertThat(large.areaHectares()).isBetween(new BigDecimal("10.7"), new BigDecimal("11.1"));
    }

    @Test
    void polygon_holeIsSubtracted() {
        List<double[]> hole = new ArrayList<>(square(108.051, 12.661, 0.001));
        Collections.reverse(hole);
        PlotGeometry withHole = PlotGeometries.polygon(square(108.05, 12.66, 0.003), List.of(hole));
        PlotGeometry full = PlotGeometries.polygon(square(108.05, 12.66, 0.003), List.of());

        assertThat(full.areaHectares().subtract(withHole.areaHectares()))
                .isBetween(new BigDecimal("1.18"), new BigDecimal("1.24"));
    }

    @Test
    void polygon_selfIntersecting_isRejected() {
        List<double[]> bowtie = List.of(new double[]{108.05, 12.66}, new double[]{108.051, 12.661},
                new double[]{108.051, 12.66}, new double[]{108.05, 12.661}, new double[]{108.05, 12.66});

        assertThatThrownBy(() -> PlotGeometries.polygon(bowtie, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid polygon");
    }

    @Test
    void polygon_openOrTooShortRing_isRejected() {
        List<double[]> open = square(108.05, 12.66, 0.001).subList(0, 4);
        assertThatThrownBy(() -> PlotGeometries.polygon(open, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("closed");
        assertThatThrownBy(() -> PlotGeometries.polygon(open.subList(0, 3), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 4");
    }

    @Test
    void polygon_sameShapeFromAnotherStartPointOrDirection_hashesEqual() {
        List<double[]> ring = square(108.05, 12.66, 0.002);
        List<double[]> rotated = List.of(ring.get(2), ring.get(3), ring.get(0), ring.get(1), ring.get(2));
        List<double[]> reversed = new ArrayList<>(ring);
        Collections.reverse(reversed);

        String hash = PlotGeometries.polygon(ring, List.of()).geometryHash();
        assertThat(PlotGeometries.polygon(rotated, List.of()).geometryHash()).isEqualTo(hash);
        assertThat(PlotGeometries.polygon(reversed, List.of()).geometryHash()).isEqualTo(hash);
        assertThat(PlotGeometries.polygon(square(108.05, 12.66, 0.0021), List.of()).geometryHash()).isNotEqualTo(hash);
    }
}
