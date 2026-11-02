package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.vo.ForestBaselineAssessment;
import com.agricontract.product.domain.model.vo.ForestClass;
import com.agricontract.product.domain.model.vo.ForestOverlap;
import com.agricontract.product.domain.model.vo.PlotGeometry;
import com.agricontract.product.domain.repository.ForestBaselineRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spatial lookups against the locally loaded 2020 forest baseline (V9). Both tables carry SRID 4326 spatial indexes,
 * so ST_Within/ST_Intersects only test the candidates whose bounding boxes touch the plot.
 */
@Repository
public class ForestBaselineRepositoryImpl implements ForestBaselineRepository {

    private static final String PLOT = "ST_GeomFromText(?, 4326, 'axis-order=long-lat')";

    private final JdbcTemplate jdbcTemplate;
    private final String datasetVersion;

    public ForestBaselineRepositoryImpl(JdbcTemplate jdbcTemplate,
                                        @Value("${plot.forest-baseline.dataset-version}") String datasetVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasetVersion = datasetVersion;
    }

    @Override
    public Optional<ForestBaselineAssessment> assess(PlotGeometry geometry) {
        String wkt = geometry.wkt();
        Boolean covered = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM forest_baseline_coverage
                                       WHERE dataset_version = ? AND ST_Within(%s, extent))""".formatted(PLOT),
                Boolean.class, datasetVersion, wkt);
        if (!Boolean.TRUE.equals(covered)) {
            return Optional.empty();
        }
        List<ForestOverlap> overlaps = jdbcTemplate.query("""
                        SELECT baseline_area_id, forest_class FROM forest_baseline_area
                        WHERE dataset_version = ? AND ST_Intersects(geom, %s)""".formatted(PLOT),
                (rs, i) -> new ForestOverlap(rs.getString(1), ForestClass.valueOf(rs.getString(2))),
                datasetVersion, wkt);
        return Optional.of(new ForestBaselineAssessment(datasetVersion, overlaps));
    }
}
