package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.ListingReservationResponse;
import com.agricontract.product.application.dto.ReleaseListingReservationRequest;
import com.agricontract.product.application.dto.ReservationResult;
import com.agricontract.product.application.dto.ReserveListingQuantityRequest;
import com.agricontract.product.application.usecase.ReleaseListingReservationUseCase;
import com.agricontract.product.application.usecase.ReserveListingQuantityUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service only (contract-service); HeaderAuthenticationFilter rejects /internal/** without
 * X-Internal-Secret. Bodies are bare DTOs per the frozen supplementary API, not ApiResponse-wrapped.
 */
@RestController
@RequestMapping("/internal/v1/listing-reservations")
@RequiredArgsConstructor
public class ListingReservationController {

    private final ReserveListingQuantityUseCase reserveListingQuantityUseCase;
    private final ReleaseListingReservationUseCase releaseListingReservationUseCase;

    @PostMapping
    public ResponseEntity<ListingReservationResponse> reserve(@RequestBody @Valid ReserveListingQuantityRequest request) {
        ReservationResult result = reserveListingQuantityUseCase.execute(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.reservation());
    }

    @PostMapping("/{contractId}/release")
    public ResponseEntity<Void> release(@PathVariable String contractId,
                                        @RequestBody @Valid ReleaseListingReservationRequest request) {
        releaseListingReservationUseCase.execute(contractId, request);
        return ResponseEntity.noContent().build();
    }
}
