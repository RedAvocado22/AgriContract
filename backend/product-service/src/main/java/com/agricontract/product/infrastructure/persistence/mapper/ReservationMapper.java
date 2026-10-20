package com.agricontract.product.infrastructure.persistence.mapper;

import com.agricontract.product.domain.model.Reservation;
import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.infrastructure.persistence.entity.ReservationJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    default ReservationJpaEntity toJpaEntity(Reservation domain) {
        return ReservationJpaEntity.builder()
                .contractId(domain.getContractId().value())
                .listingId(domain.getListingId().value())
                .reservedQuantity(domain.getReservedQuantity())
                .status(domain.getStatus())
                .build();
    }

    default Reservation toDomain(ReservationJpaEntity entity) {
        return Reservation.reconstruct(
                new ContractId(entity.getContractId()),
                new ListingId(entity.getListingId()),
                entity.getReservedQuantity(),
                entity.getStatus()
        );
    }
}
