package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.application.port.ListingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListingPortAdapter implements ListingPort {

    private final ProductServiceClient productServiceClient;

    @Override
    public ListingResponse getListing(String listingId) {
        return productServiceClient.getListing(listingId).getData();
    }

    @Override
    public void closeListing(String listingId) {
        productServiceClient.closeListing(listingId);
    }
}
