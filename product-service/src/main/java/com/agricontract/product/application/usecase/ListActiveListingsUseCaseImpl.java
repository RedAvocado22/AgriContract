package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListActiveListingsUseCaseImpl implements ListActiveListingsUseCase {
    private final ListingRepository listingRepository;

    @Override
    public Page<ListingResponse> execute(Pageable pageable) {
        Page<Listing> listing = listingRepository.findByStatus(ListingStatus.ACTIVE, pageable);

        return listing.map(this::toResponse);
    }

    private ListingResponse toResponse(Listing listing) {
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
}
