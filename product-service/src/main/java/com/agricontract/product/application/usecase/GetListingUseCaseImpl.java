package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.common.exception.ListingNotFoundException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetListingUseCaseImpl implements GetListingUseCase {
    private final ListingRepository listingRepository;

    @Override
    public ListingResponse execute(String listingId) {
        Listing listing = listingRepository.findById(new ListingId(listingId))
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        return toResponse(listing);
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
