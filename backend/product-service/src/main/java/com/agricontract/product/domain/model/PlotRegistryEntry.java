package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.GeoRiskLevel;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.domain.model.vo.GeometryType;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.model.vo.VerificationLevel;
import lombok.Getter;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;

// Aggregate Root — one plot of one member household, owned by the seller, reused across listings (design §2.1).
// Identified for re-import by (sellerId, householdLabel).
// Geo check: PENDING → CHECKED | UNVERIFIED; a geometry change starts it again at PENDING.
@Getter
public class PlotRegistryEntry {

    // household_label VARCHAR(255) utf8mb4 counts characters, i.e. code points
    public static final int MAX_LABEL_LENGTH = 255;

    private RegistryEntryId registryEntryId;
    private String sellerId;
    private String householdLabel;
    private PlotGeometry geometry;
    private PlotSource source;
    // MANUAL_PIN only (browser coords.accuracy); a UI warning, never a block
    private BigDecimal accuracyMeters;
    private VerificationLevel verificationLevel;
    private Province declaredProvince;
    private GeoVerificationStatus geoVerificationStatus;
    // set only when geoVerificationStatus = CHECKED
    private GeoRiskLevel geoRiskLevel;
    // last create or re-import, not the first registration
    private Instant importedAt;

    private PlotRegistryEntry() {
    }

    public static PlotRegistryEntry register(RegistryEntryId registryEntryId, String sellerId, String householdLabel,
                                             PlotGeometry geometry, PlotSource source, BigDecimal accuracyMeters,
                                             Province declaredProvince, Instant now) {
        if (registryEntryId == null) throw new IllegalArgumentException("registryEntryId must not be null");
        if (sellerId == null || sellerId.isBlank()) throw new IllegalArgumentException("sellerId must not be blank");
        if (householdLabel == null || householdLabel.isBlank())
            throw new IllegalArgumentException("householdLabel must not be blank");

        PlotRegistryEntry entry = new PlotRegistryEntry();
        entry.registryEntryId = registryEntryId;
        entry.sellerId = sellerId;
        entry.householdLabel = normalizeLabel(householdLabel);
        entry.verificationLevel = VerificationLevel.SELF_DECLARED;
        entry.apply(geometry, source, accuracyMeters, declaredProvince, now);
        entry.geoVerificationStatus = GeoVerificationStatus.PENDING;
        return entry;
    }

    public static PlotRegistryEntry reconstruct(RegistryEntryId registryEntryId, String sellerId, String householdLabel,
                                                PlotGeometry geometry, PlotSource source, BigDecimal accuracyMeters,
                                                VerificationLevel verificationLevel, Province declaredProvince,
                                                GeoVerificationStatus geoVerificationStatus, GeoRiskLevel geoRiskLevel,
                                                Instant importedAt) {
        PlotRegistryEntry entry = new PlotRegistryEntry();
        entry.registryEntryId = registryEntryId;
        entry.sellerId = sellerId;
        entry.householdLabel = householdLabel;
        entry.geometry = geometry;
        entry.source = source;
        entry.accuracyMeters = accuracyMeters;
        entry.verificationLevel = verificationLevel;
        entry.declaredProvince = declaredProvince;
        entry.geoVerificationStatus = geoVerificationStatus;
        entry.geoRiskLevel = geoRiskLevel;
        entry.importedAt = importedAt;
        return entry;
    }

    /**
     * Re-import or re-pin of the same household (design §4): updates this entry in place. Listings keep the copy
     * they took (design §2.4). The geo check only restarts when the geometry really changed, so replaying the same
     * import does not throw away a finished check.
     */
    public void reimport(PlotGeometry geometry, PlotSource source, BigDecimal accuracyMeters,
                         Province declaredProvince, Instant now) {
        boolean geometryChanged = !geometry.geometryHash().equals(this.geometry.geometryHash());
        apply(geometry, source, accuracyMeters, declaredProvince, now);
        if (geometryChanged) {
            this.geoVerificationStatus = GeoVerificationStatus.PENDING;
            this.geoRiskLevel = null;
        }
    }

    // A PENDING plot cannot back a listing yet: the listing would go live before its forest risk is known (§2.2.4).
    public boolean canBackListing() {
        return geoVerificationStatus != GeoVerificationStatus.PENDING;
    }

    public boolean isOwnedBy(String sellerId) {
        return this.sellerId.equals(sellerId);
    }

    /**
     * The re-import match key. KML tools differ in Unicode form (NFC vs NFD) for Vietnamese diacritics, so
     * "Hộ Lê Văn A" from two exports must compare equal; diacritics themselves stay significant.
     * The length is checked after NFC, the form that is stored.
     */
    public static String normalizeLabel(String householdLabel) {
        String label = Normalizer.normalize(householdLabel.trim(), Normalizer.Form.NFC);
        if (label.codePointCount(0, label.length()) > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Household label is longer than " + MAX_LABEL_LENGTH + " characters");
        }
        return label;
    }

    private void apply(PlotGeometry geometry, PlotSource source, BigDecimal accuracyMeters,
                       Province declaredProvince, Instant now) {
        if (geometry == null) throw new IllegalArgumentException("geometry must not be null");
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (declaredProvince == null) throw new IllegalArgumentException("declaredProvince must not be null");
        if (now == null) throw new IllegalArgumentException("now must not be null");
        if (source == PlotSource.MANUAL_PIN && geometry.type() != GeometryType.POINT) {
            throw new IllegalArgumentException("A manual pin can only declare a POINT plot");
        }
        if (accuracyMeters != null && (source != PlotSource.MANUAL_PIN || accuracyMeters.signum() < 0)) {
            throw new IllegalArgumentException("accuracyMeters is only for a manual pin and must not be negative");
        }
        this.geometry = geometry;
        this.source = source;
        this.accuracyMeters = accuracyMeters;
        this.declaredProvince = declaredProvince;
        this.importedAt = now;
    }
}
