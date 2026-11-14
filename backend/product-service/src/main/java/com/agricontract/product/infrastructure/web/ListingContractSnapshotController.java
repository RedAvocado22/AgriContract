package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.ListingContractSnapshotResponse;
import com.agricontract.product.application.usecase.GetListingContractSnapshotUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only (contract-service); HeaderAuthenticationFilter rejects /internal/** without
 * X-Internal-Secret. Bare body per the supplementary API.
 */
@RestController
@RequiredArgsConstructor
public class ListingContractSnapshotController {

    private final GetListingContractSnapshotUseCase getListingContractSnapshotUseCase;

    @GetMapping("/internal/v1/listings/{listingId}/contract-snapshot")
    public ListingContractSnapshotResponse get(@PathVariable String listingId,
                                               @RequestParam String listingVersionToken) {
        return getListingContractSnapshotUseCase.execute(listingId, listingVersionToken);
    }
}
