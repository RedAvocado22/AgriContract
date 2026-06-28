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
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateListingUseCaseImpl implements CreateListingUseCase {

    private final ListingRepository listingRepository;
    private final ProductRepository productRepository;

    @Override
    public ListingResponse execute(String sellerId, CreateListingRequest request) {
        Product product = productRepository.findById(new ProductId(request.productId()))
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        Listing listing = Listing.create(
                new ListingId(UUID.randomUUID().toString()),
                sellerId,
                new ProductId(request.productId()),
                product.getName(),
                new Quantity(request.quantity(), request.quantityUnit()),
                new Money(request.priceFloor(), request.currency()),
                request.deliveryDeadline()
        );

        Listing saved = listingRepository.save(listing);

        return ListingResponse.builder()
                .listingId(saved.getListingId().value())
                .sellerId(saved.getSellerId())
                .productId(saved.getProductId().value())
                .productName(saved.getProductName())
                .quantity(saved.getQuantity().value())
                .quantityUnit(saved.getQuantity().unit())
                .priceFloor(saved.getPriceFloor().amount())
                .currency(saved.getPriceFloor().currency())
                .deliveryDeadline(saved.getDeliveryDeadline())
                .status(saved.getStatus())
                .build();
    }
}
