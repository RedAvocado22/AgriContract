package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.vo.GeoVerificationStatus;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.infrastructure.persistence.mapper.PlotRegistryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PlotRegistryRepositoryImpl implements PlotRegistryRepository {
    private final PlotRegistryMapper plotRegistryMapper;
    private final PlotRegistryEntryJpaRepository plotRegistryEntryJpaRepository;

    @Override
    public Optional<PlotRegistryEntry> findBySellerIdAndHouseholdLabel(String sellerId, String householdLabel) {
        return plotRegistryEntryJpaRepository.findBySellerIdAndHouseholdLabel(sellerId, householdLabel)
                .map(plotRegistryMapper::toDomain);
    }

    @Override
    public List<PlotRegistryEntry> findAllById(Collection<RegistryEntryId> registryEntryIds) {
        return plotRegistryEntryJpaRepository.findAllById(registryEntryIds.stream().map(RegistryEntryId::value).toList())
                .stream().map(plotRegistryMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public PlotRegistryEntry create(PlotRegistryEntry entry) {
        // flush so a concurrent insert of the same (seller, household) fails here on the unique key
        return plotRegistryMapper.toDomain(
                plotRegistryEntryJpaRepository.saveAndFlush(plotRegistryMapper.toJpaEntity(entry)));
    }

    @Override
    @Transactional
    public PlotRegistryEntry update(PlotRegistryEntry entry) {
        var entity = plotRegistryEntryJpaRepository.findById(entry.getRegistryEntryId().value())
                .orElseThrow(() -> new IllegalStateException("Plot " + entry.getRegistryEntryId().value() + " not found"));
        plotRegistryMapper.copyMutable(entry, entity);
        plotRegistryEntryJpaRepository.saveAndFlush(entity);
        return entry;
    }

    @Override
    @Transactional
    public int markUnverifiedImportedBefore(Instant cutoff) {
        return plotRegistryEntryJpaRepository.transitionImportedBefore(GeoVerificationStatus.PENDING,
                GeoVerificationStatus.UNVERIFIED, LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC));
    }
}
