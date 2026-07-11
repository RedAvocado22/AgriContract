package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingQueryRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.application.usecase.*;
import com.agricontract.product.common.ApiResponse;
import com.agricontract.product.common.PaginatedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    @Value("${service.internal-secret}")
    private String serviceInternalSecret;

    private final GetListingUseCase getListingUseCase;
    private final CloseListingUseCase closeListingUseCase;
    private final ListActiveListingsUseCase listActiveListingsUseCase;
    private final CreateListingUseCase createListingUseCase;
    private final GetSellerListingsUseCase getSellerListingsUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<ListingResponse>>> getActiveListings(
            @ModelAttribute @Valid ListingQueryRequest request) {
        Pageable pageable = request.toPageable();
        PagedResult<ListingResponse> result = listActiveListingsUseCase.execute(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.from(result, pageable)));
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ApiResponse<ListingResponse>> getListing(@PathVariable String listingId) {
        ListingResponse response = getListingUseCase.execute(listingId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/seller")
    public ResponseEntity<ApiResponse<PaginatedResponse<ListingResponse>>> getSellerListings(
            @ModelAttribute @Valid ListingQueryRequest request,
            @RequestHeader("X-User-Id") String sellerId) {
        Pageable pageable = request.toPageable();
        PagedResult<ListingResponse> result = getSellerListingsUseCase.execute(sellerId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.from(result, pageable)));
    }

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ListingResponse>> createListing(
            @RequestBody @Valid CreateListingRequest request,
            @RequestHeader("X-User-Id") String sellerId) {
        ListingResponse response = createListingUseCase.execute(sellerId, request);
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @PutMapping("/{listingId}/close")
    public ResponseEntity<ApiResponse<Void>> closeListing(
            @PathVariable String listingId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String internalSecret) {
        if (!serviceInternalSecret.equals(internalSecret)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Forbidden"));
        }
        closeListingUseCase.execute(listingId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
