package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.ListingReservation;
import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.common.ApiResponse;
import com.agricontract.contract.domain.model.terms.CoffeeGoodsTerms;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "product-service", url = "${product-service.url}", configuration = FeignConfig.class)
public interface ProductServiceClient {

    @GetMapping("/api/v1/listings/{listingId}")
    ApiResponse<ListingResponse> getListing(@PathVariable("listingId") String listingId);

    @PutMapping("/api/v1/listings/{listingId}/close")
    ApiResponse<Void> closeListing(@PathVariable("listingId") String listingId);

    // Service-to-service (supplementary-services-api.yaml): bare bodies, X-Internal-Secret from FeignConfig.

    @GetMapping("/internal/v1/listings/{listingId}/contract-snapshot")
    CoffeeGoodsTerms getContractSnapshot(@PathVariable("listingId") String listingId,
                                         @RequestParam("listingVersionToken") String listingVersionToken);

    @PostMapping("/internal/v1/listing-reservations")
    ListingReservation reserve(@RequestBody ReserveRequest request);

    @PostMapping("/internal/v1/listing-reservations/{contractId}/release")
    void release(@PathVariable("contractId") String contractId, @RequestBody ReleaseRequest request);

    /** ReserveListingQuantityRequest. */
    record ReserveRequest(String contractId, String listingId, BigDecimal requestedQuantity,
                          String listingVersionToken) {
    }

    /** ReleaseListingReservationRequest; reason is always CREATE_PERSISTENCE_COMPENSATION. */
    record ReleaseRequest(String listingId, BigDecimal reservedQuantity, String reason) {
    }
}
