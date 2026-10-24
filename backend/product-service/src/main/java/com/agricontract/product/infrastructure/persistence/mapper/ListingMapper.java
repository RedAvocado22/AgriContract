package com.agricontract.product.infrastructure.persistence.mapper;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CoffeeQualitySpec;
import com.agricontract.product.domain.model.vo.ListingGoods;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.model.vo.Quantity;
import com.agricontract.product.infrastructure.persistence.entity.ListingJpaEntity;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ListingMapper {

    default ListingJpaEntity toJpaEntity(Listing domain) {
        return ListingJpaEntity.builder()
                .listingId(domain.getListingId().value())
                .sellerId(domain.getSellerId())
                .productId(domain.getProductId().value())
                .productName(domain.getProductName())
                .coverImageUrl(domain.getCoverImageUrl())
                .quantity(domain.getQuantity().value())
                .quantityUnit(domain.getQuantity().unit())
                .quantityAvailable(domain.getQuantityAvailable())
                .minOrderQuantity(domain.getMinOrderQuantity())
                .priceFloor(domain.getPriceFloor().amount())
                .currency(domain.getPriceFloor().currency())
                .deliveryDeadline(domain.getDeliveryDeadline())
                .status(domain.getStatus())
                .categoryId(domain.getGoods().categoryId() == null ? null : domain.getGoods().categoryId().value())
                .commodity(domain.getGoods().commodity())
                .cropYear(domain.getGoods().cropYear())
                .harvestSeason(domain.getGoods().harvestSeason())
                .certifications(domain.getGoods().certifications())
                .regionText(domain.getGoods().regionText())
                .qualityType(spec(domain) == null ? null : spec(domain).type())
                .qualityMoisturePercent(spec(domain) == null ? null : spec(domain).moisturePercent())
                .qualityForeignMatterPercent(spec(domain) == null ? null : spec(domain).foreignMatterPercent())
                .qualityBlackBrokenPercent(spec(domain) == null ? null : spec(domain).blackBrokenBeansPercent())
                .packagingDeclaration(domain.getGoods().packagingDeclaration())
                .warehouseLocation(domain.getGoods().warehouseLocation())
                .estimatedAvailabilityDate(domain.getGoods().estimatedAvailabilityDate())
                .traceabilityEvidencePackageId(domain.getGoods().traceabilityEvidencePackageId())
                .build();
    }

    private static CoffeeQualitySpec spec(Listing listing) {
        return listing.getGoods().declaredQualitySpec();
    }

    default ListingResponse toResponse(Listing listing) {
        return ListingResponse.from(listing);
    }

    default Listing toDomain(ListingJpaEntity entity, List<ListingPlot> plots) {
        return Listing.reconstruct(
                new ListingId(entity.getListingId()),
                entity.getSellerId(),
                new ProductId(entity.getProductId()),
                entity.getProductName(),
                entity.getCoverImageUrl(),
                new Quantity(entity.getQuantity(), entity.getQuantityUnit()),
                entity.getQuantityAvailable(),
                entity.getMinOrderQuantity(),
                new Money(entity.getPriceFloor(), entity.getCurrency()),
                entity.getDeliveryDeadline(),
                entity.getStatus(),
                entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toInstant(ZoneOffset.UTC),
                plots,
                new ListingGoods(
                        entity.getCategoryId() == null ? null : new CategoryId(entity.getCategoryId()),
                        entity.getCommodity(),
                        entity.getCropYear(),
                        entity.getHarvestSeason(),
                        entity.getCertifications(),
                        entity.getRegionText(),
                        entity.getQualityType() == null ? null : new CoffeeQualitySpec(entity.getQualityType(),
                                entity.getQualityMoisturePercent(), entity.getQualityForeignMatterPercent(),
                                entity.getQualityBlackBrokenPercent()),
                        entity.getPackagingDeclaration(),
                        entity.getWarehouseLocation(),
                        entity.getEstimatedAvailabilityDate(),
                        entity.getTraceabilityEvidencePackageId())
        );
    }

    /**
     * Inverse of {@link Listing#getVersionToken()}: the stored UTC updated_at the token was issued from.
     */
    static LocalDateTime fromVersionToken(String versionToken) {
        return LocalDateTime.ofInstant(Instant.parse(versionToken), ZoneOffset.UTC);
    }
}
