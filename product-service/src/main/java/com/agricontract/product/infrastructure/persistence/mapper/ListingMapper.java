package com.agricontract.product.infrastructure.persistence.mapper;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.model.vo.Quantity;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ListingMapper {

    default ListingJpaEntity toJpaEntity(Listing domain) {
        return ListingJpaEntity.builder()
                .listingId(domain.getListingId().value())
                .sellerId(domain.getSellerId())
                .productId(domain.getProductId().value())
                .productName(domain.getProductName())
                .quantity(domain.getQuantity().value())
                .quantityUnit(domain.getQuantity().unit())
                .priceFloor(domain.getPriceFloor().amount())
                .currency(domain.getPriceFloor().currency())
                .deliveryDeadline(domain.getDeliveryDeadline())
                .status(domain.getStatus())
                .build();
    }

    default ListingResponse toResponse(Listing listing) {
        return ListingResponse.builder()
                .listingId(listing.getListingId().value())
                .sellerId(listing.getSellerId())
                .productId(listing.getProductId().value())
                .productName(listing.getProductName())
                .quantity(listing.getQuantity().value())
                .quantityUnit(listing.getQuantity().unit())
                .priceFloor(listing.getPriceFloor().amount())
                .currency(listing.getPriceFloor().currency())
                .deliveryDeadline(listing.getDeliveryDeadline())
                .status(listing.getStatus())
                .build();
    }

    default Listing toDomain(ListingJpaEntity entity) {
        return Listing.reconstruct(
                new ListingId(entity.getListingId()),
                entity.getSellerId(),
                new ProductId(entity.getProductId()),
                entity.getProductName(),
                new Quantity(entity.getQuantity(), entity.getQuantityUnit()),
                new Money(entity.getPriceFloor(), entity.getCurrency()),
                entity.getDeliveryDeadline(),
                entity.getStatus()
        );
    }
}
