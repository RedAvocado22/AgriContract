package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.infrastructure.persistence.entity.PlotGeoRiskCheckJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlotGeoRiskCheckJpaRepository extends JpaRepository<PlotGeoRiskCheckJpaEntity, Long> {

    List<PlotGeoRiskCheckJpaEntity> findByRegistryEntryIdOrderByCheckedAtAsc(String registryEntryId);
}
