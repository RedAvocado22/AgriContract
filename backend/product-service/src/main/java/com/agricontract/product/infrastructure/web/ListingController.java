package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingQueryRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.ListingSearchQuery;
import com.agricontract.product.application.dto.ListingSearchRequest;
import com.agricontract.product.application.dto.ListingSearchResponse;
import com.agricontract.product.application.dto.ListingSearchResult;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.application.dto.ReputationViewer;
import com.agricontract.product.application.usecase.*;
import com.agricontract.product.common.ApiResponse;
import com.agricontract.product.common.PaginatedResponse;
import com.agricontract.product.domain.repository.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // Public search. A signed-in viewer also gets seller reputation and may filter by it (reputation design §6.2);
    // the viewer comes from the gateway-authenticated principal, never from a raw header.
    @GetMapping
    public ResponseEntity<ApiResponse<ListingSearchResponse>> getActiveListings(
            @ModelAttribute @Valid ListingSearchRequest request, Authentication authentication) {
        PageQuery page = request.toPageQuery();
        ListingSearchResult result = listActiveListingsUseCase.execute(
                new ListingSearchQuery(page, request.getMinSellerScore(), viewer(authentication)));
        return ResponseEntity.ok(ApiResponse.ok(new ListingSearchResponse(
                PaginatedResponse.from(result.page(), page), result.reputationStatus())));
    }

    private static ReputationViewer viewer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String userId)) {
            return null;
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .toList();
        return roles.isEmpty() ? null : new ReputationViewer(userId, roles);
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
        PageQuery page = request.toPageQuery();
        PagedResult<ListingResponse> result = getSellerListingsUseCase.execute(sellerId, page);
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.from(result, page)));
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
