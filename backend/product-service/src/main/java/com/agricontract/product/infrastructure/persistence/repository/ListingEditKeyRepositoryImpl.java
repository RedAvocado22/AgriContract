package com.agricontract.product.infrastructure.persistence.repository;

import com.agricontract.product.common.exception.WriteConflictException;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.repository.ListingEditKeyRepository;
import com.agricontract.product.infrastructure.persistence.entity.ListingEditKeyJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ListingEditKeyRepositoryImpl implements ListingEditKeyRepository {

    private final ListingEditKeyJpaRepository jpaRepository;

    @Override
    public Optional<EditKey> find(String sellerId, String idempotencyKey) {
        return jpaRepository.findBySellerIdAndIdempotencyKey(sellerId, idempotencyKey)
                .map(row -> new EditKey(new ListingId(row.getListingId()), row.getRequestFingerprint()));
    }

    @Override
    public void record(String sellerId, String idempotencyKey, EditKey editKey) {
        try {
            jpaRepository.saveAndFlush(ListingEditKeyJpaEntity.builder()
                    .sellerId(sellerId)
                    .idempotencyKey(idempotencyKey)
                    .listingId(editKey.listingId().value())
                    .requestFingerprint(editKey.requestFingerprint())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new WriteConflictException("Idempotency-Key " + idempotencyKey + " was already used", e);
        }
    }
}
