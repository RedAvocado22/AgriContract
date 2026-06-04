package com.agricontract.contract.infrastructure.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Sync call to product-service to CLOSE a listing when a contract is SIGNED.
 * This is intentionally synchronous — contract-service must confirm the listing
 * is closed before emitting contract.signed to prevent two contracts referencing the same listing.
 */
@FeignClient(name = "product-service", url = "${product-service.url}")
public interface ProductServiceClient {

    @PutMapping("/api/v1/listings/{listingId}/close")
    void closeListing(@PathVariable("listingId") String listingId);
}
