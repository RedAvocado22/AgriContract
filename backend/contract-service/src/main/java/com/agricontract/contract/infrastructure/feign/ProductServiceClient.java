package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "product-service", url = "${product-service.url}", configuration = FeignConfig.class)
public interface ProductServiceClient {

    @GetMapping("/api/v1/listings/{listingId}")
    ApiResponse<ListingResponse> getListing(@PathVariable("listingId") String listingId);

    @PutMapping("/api/v1/listings/{listingId}/close")
    ApiResponse<Void> closeListing(@PathVariable("listingId") String listingId);
}
