package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.ListingQueryRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.usecase.CloseListingUseCase;
import com.agricontract.product.application.usecase.GetListingUseCase;
import com.agricontract.product.application.usecase.ListActiveListingsUseCase;
import com.agricontract.product.common.ApiResponse;
import com.agricontract.product.common.PaginatedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {
    private final GetListingUseCase getListingUseCase;
    private final CloseListingUseCase closeListingUseCase;
    private final ListActiveListingsUseCase listActiveListingsUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<ListingResponse>>> getActiveListings(@ModelAttribute @Valid ListingQueryRequest request) {
        Page<ListingResponse> result = listActiveListingsUseCase.execute(request.toPageable());
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.from(result)));
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ApiResponse<ListingResponse>> getListing(@PathVariable String listingId) {
        ListingResponse response = getListingUseCase.execute(listingId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping
    public ResponseEntity<?> createListing(@RequestBody Object request) {
        // TODO
        return ResponseEntity.status(201).build();
    }

    /**
     * Called by contract-service via OpenFeign to close a listing when contract is SIGNED.
     */
    @PutMapping("/{listingId}/close")
    public ResponseEntity<ApiResponse<Void>> closeListing(
            @PathVariable String listingId,
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall) {

        if (!"true".equals(internalCall)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Forbidden"));
        }

        closeListingUseCase.execute(listingId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

}
