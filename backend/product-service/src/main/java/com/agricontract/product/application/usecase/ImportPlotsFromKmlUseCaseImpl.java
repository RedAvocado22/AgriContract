package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.PlotImportResult;
import com.agricontract.product.application.dto.PlotResponse;
import com.agricontract.product.application.dto.RegisteredPlot;
import com.agricontract.product.application.kml.KmlPlacemark;
import com.agricontract.product.application.kml.KmlPlacemarkReader;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import com.agricontract.product.domain.model.vo.PlotSource;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.product.domain.service.PlotGeometries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Each placemark is validated and stored on its own: a bad one is reported by name and the rest of the file
 * still goes in (design §4). declaredProvince applies to every placemark of the file.
 */
@Service
@RequiredArgsConstructor
public class ImportPlotsFromKmlUseCaseImpl implements ImportPlotsFromKmlUseCase {

    static final int MAX_PLACEMARKS = 1000;

    private final PlotRegistrar plotRegistrar;

    @Override
    public PlotImportResult execute(String sellerId, byte[] kml, Province declaredProvince) {
        if (declaredProvince == null) {
            throw new IllegalArgumentException("declaredProvince is required");
        }
        List<KmlPlacemark> placemarks = KmlPlacemarkReader.read(kml);
        if (placemarks.isEmpty()) {
            throw new IllegalArgumentException("KML contains no Placemark");
        }
        if (placemarks.size() > MAX_PLACEMARKS) {
            throw new IllegalArgumentException("KML has " + placemarks.size() + " placemarks; split files above "
                    + MAX_PLACEMARKS);
        }

        List<PlotResponse> created = new ArrayList<>();
        List<PlotResponse> updated = new ArrayList<>();
        List<PlotImportResult.Rejection> rejected = new ArrayList<>();
        Set<String> seenLabels = new HashSet<>();

        for (KmlPlacemark placemark : placemarks) {
            try {
                if (placemark.error() != null) {
                    throw new IllegalArgumentException(placemark.error());
                }
                // a second placemark of the same household in one file would silently overwrite the first
                if (!seenLabels.add(PlotRegistryEntry.normalizeLabel(placemark.name()))) {
                    throw new IllegalArgumentException("Household label appears more than once in this file");
                }
                RegisteredPlot result = plotRegistrar.register(sellerId, placemark.name(), geometry(placemark),
                        PlotSource.KML_IMPORT, null, declaredProvince);
                (result.created() ? created : updated).add(result.plot());
            } catch (IllegalArgumentException e) {
                rejected.add(new PlotImportResult.Rejection(placemark.name(), e.getMessage()));
            }
        }
        return new PlotImportResult(created, updated, rejected);
    }

    private static PlotGeometry geometry(KmlPlacemark placemark) {
        return switch (placemark.kind()) {
            case POINT -> {
                if (placemark.areaHectares() == null) {
                    // 0 or null would read as "no land"; the seller must state it (design §4)
                    throw new IllegalArgumentException(
                            "Point placemark needs its area in ExtendedData area_ha or dien_tich");
                }
                double[] c = placemark.shell().get(0);
                yield PlotGeometries.point(c[0], c[1], placemark.areaHectares());
            }
            case POLYGON -> PlotGeometries.polygon(placemark.shell(), placemark.holes());
            case REJECTED -> throw new IllegalArgumentException(placemark.error());
        };
    }
}
