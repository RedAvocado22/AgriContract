package com.agricontract.product.testsupport;

import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import com.agricontract.product.domain.service.PlotGeometries;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

public final class PlotFixtures {

    public static final String PLOT_ID = "plot-1";

    private PlotFixtures() {
    }

    // a 1.5 ha point near Buon Ma Thuot, Dak Lak
    public static PlotGeometry point() {
        return PlotGeometries.point(108.050123, 12.666789, new BigDecimal("1.5"));
    }

    public static ListingPlot listingPlot(String registryEntryId) {
        return new ListingPlot(new RegistryEntryId(registryEntryId), "Ho Nguyen Van A", point(),
                VerificationLevel.SELF_DECLARED);
    }

    public static List<ListingPlot> onePlot() {
        return List.of(listingPlot(PLOT_ID));
    }

    /** Registry row a listing_plot copy can point to (FK), already past the geo check. */
    public static void insertRegistryEntry(JdbcTemplate jdbc, String registryEntryId, String sellerId) {
        PlotGeometry geometry = point();
        jdbc.update("""
                        INSERT INTO plot_registry_entry (registry_entry_id, seller_id, household_label, geometry_type,
                            geo_json, geometry_hash, area_hectares, source, declared_province,
                            geo_verification_status, imported_at)
                        VALUES (?, ?, ?, 'POINT', ST_GeomFromText(?, 4326, 'axis-order=long-lat'), ?, ?, 'MANUAL_PIN',
                            'DAK_LAK', 'UNVERIFIED', UTC_TIMESTAMP(6))""",
                registryEntryId, sellerId, "Ho " + registryEntryId, geometry.wkt(), geometry.geometryHash(),
                geometry.areaHectares());
    }

    /** Child tables first, so the foreign keys allow the deletes. */
    public static void deletePlotsAndListings(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM listing_plot");
        jdbc.update("DELETE FROM listing_reservations");
        jdbc.update("DELETE FROM listings");
        jdbc.update("DELETE FROM plot_registry_entry");
    }
}
