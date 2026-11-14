package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.CreatedListing;
import com.agricontract.product.application.dto.ListingQueryRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.ListingSearchQuery;
import com.agricontract.product.application.dto.ListingSearchRequest;
import com.agricontract.product.application.dto.ListingSearchResponse;
import com.agricontract.product.application.dto.ListingSearchResult;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.application.dto.ReputationViewer;
import com.agricontract.product.application.usecase.*;
import com.agricontract.product.common.PaginatedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

    private final GetListingUseCase getListingUseCase;
    private final ListActiveListingsUseCase listActiveListingsUseCase;
    private final CreateListingUseCase createListingUseCase;
    private final GetSellerListingsUseCase getSellerListingsUseCase;

    // Public search. A signed-in viewer also gets seller reputation and may filter by it (reputation design §6.2);
    // the viewer comes from the gateway-authenticated principal, never from a raw header.
    @GetMapping
    public ResponseEntity<ListingSearchResponse> getActiveListings(
            @ModelAttribute @Valid ListingSearchRequest request, Authentication authentication) {
        Pageable pageable = request.toPageable();
        ListingSearchResult result = listActiveListingsUseCase.execute(
                new ListingSearchQuery(pageable, request.getMinSellerScore(), viewer(authentication)));
        return ResponseEntity.ok(new ListingSearchResponse(
                PaginatedResponse.from(result.page(), pageable), result.reputationStatus()));
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
    public ResponseEntity<ListingResponse> getListing(@PathVariable String listingId) {
        return ResponseEntity.ok(getListingUseCase.execute(listingId));
    }

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/seller")
    public ResponseEntity<PaginatedResponse<ListingResponse>> getSellerListings(
            @ModelAttribute @Valid ListingQueryRequest request,
            @RequestHeader("X-User-Id") String sellerId) {
        Pageable pageable = request.toPageable();
        PagedResult<ListingResponse> result = getSellerListingsUseCase.execute(sellerId, pageable);
        return ResponseEntity.ok(PaginatedResponse.from(result, pageable));
    }

    /**
     * createListing (supplementary contract): Idempotency-Key is required (missing → 400). A replay of the same key
     * and body by the same seller returns the first listing with 200; the same key with another body is 409.
     */
    @PreAuthorize("hasRole('SELLER')")
    @PostMapping
    public ResponseEntity<ListingResponse> createListing(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreateListingRequest request,
            @RequestHeader("X-User-Id") String sellerId) {
        CreatedListing result = createListingUseCase.execute(sellerId, idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.listing());
    }
}
