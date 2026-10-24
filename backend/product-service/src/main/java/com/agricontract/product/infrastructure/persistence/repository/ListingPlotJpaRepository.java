package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.infrastructure.persistence.entity.ListingPlotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ListingPlotJpaRepository extends JpaRepository<ListingPlotJpaEntity, ListingPlotJpaEntity.Key> {

    List<ListingPlotJpaEntity> findByListingIdInOrderByListingIdAscHouseholdLabelAsc(Collection<String> listingIds);
}
