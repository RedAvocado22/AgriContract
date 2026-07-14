package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.model.vo.Quantity;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateListingUseCaseImpl implements CreateListingUseCase {

    private final ListingRepository listingRepository;
    private final ProductRepository productRepository;

    @Override
    public ListingResponse execute(String sellerId, CreateListingRequest request) {
        ListingId listingId = (request.listingId() != null && !request.listingId().isBlank())
                ? new ListingId(request.listingId())
                : new ListingId(UUID.randomUUID().toString());

        Optional<ListingResponse> matched = assertSameRequestOrConflict(listingId, sellerId, request);
        if (matched.isPresent()) {
            return matched.get();
        }

        Product product = productRepository.findById(new ProductId(request.productId()))
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        Listing listing = Listing.create(
                listingId,
                sellerId,
                new ProductId(request.productId()),
                product.getName(),
                product.getCoverImageUrl(),
                new Quantity(request.quantity(), request.quantityUnit()),
                new Money(request.priceFloor(), request.currency()),
                request.deliveryDeadline()
        );

        Listing saved;
        try {
            saved = listingRepository.save(listing);
        } catch (DataIntegrityViolationException e) {
            return assertSameRequestOrConflict(listingId, sellerId, request).orElseThrow(() -> e);
        }

        return toResponse(saved);
    }

    private Optional<ListingResponse> assertSameRequestOrConflict(ListingId listingId, String sellerId, CreateListingRequest request) {
        Listing existing = listingRepository.findById(listingId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        if (existing.getSellerId().equals(sellerId) &&
                existing.getProductId().equals(new ProductId(request.productId())) &&
                existing.getQuantity().equals(new Quantity(request.quantity(), request.quantityUnit())) &&
                existing.getPriceFloor().equals(new Money(request.priceFloor(), request.currency())) &&
                existing.getDeliveryDeadline().equals(request.deliveryDeadline())) {
            return Optional.of(toResponse(existing));
        }
        throw new IllegalStateException("Listing " + listingId.value() + " already exists with different attributes");
    }

    private ListingResponse toResponse(Listing listing) {
        return ListingResponse.builder()
                .listingId(listing.getListingId().value())
                .sellerId(listing.getSellerId())
                .productId(listing.getProductId().value())
                .productName(listing.getProductName())
                .coverImageUrl(listing.getCoverImageUrl())
                .quantity(listing.getQuantity().value())
                .quantityUnit(listing.getQuantity().unit())
                .priceFloor(listing.getPriceFloor().amount())
                .currency(listing.getPriceFloor().currency())
                .deliveryDeadline(listing.getDeliveryDeadline())
                .status(listing.getStatus())
                .build();
    }
}
