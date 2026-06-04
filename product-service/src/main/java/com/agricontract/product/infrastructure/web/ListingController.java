package com.agricontract.product.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    // TODO: inject use cases

    @GetMapping
    public ResponseEntity<?> getActiveListings() {
        // TODO
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<?> getListing(@PathVariable String listingId) {
        // TODO
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<?> createListing(@RequestBody Object request) {
        // TODO
        return ResponseEntity.status(201).build();
    }

    /** Called by contract-service via OpenFeign to close a listing when contract is SIGNED. */
    @PutMapping("/{listingId}/close")
    public ResponseEntity<Void> closeListing(@PathVariable String listingId) {
        // TODO
        return ResponseEntity.ok().build();
    }
}
