package com.agricontract.contract.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    @PostMapping
    public ResponseEntity<?> offerContract(@RequestBody Object request) {
        // TODO: buyer creates offer
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/{contractId}/sign")
    public ResponseEntity<?> sign(@PathVariable String contractId) {
        // TODO: seller signs → close listing (Feign) → emit contract.signed
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{contractId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String contractId,
                                     @RequestBody Object request) {
        // TODO: buyer or seller cancels (only when OFFERED)
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{contractId}/confirm-delivery")
    public ResponseEntity<?> confirmDelivery(@PathVariable String contractId) {
        // TODO: seller confirms delivery → emit goods.delivered
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{contractId}/settle")
    public ResponseEntity<?> settle(@PathVariable String contractId) {
        // TODO: buyer confirms receipt → emit contract.settled
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{contractId}/dispute")
    public ResponseEntity<?> dispute(@PathVariable String contractId,
                                      @RequestBody Object request) {
        // TODO: buyer opens dispute → emit contract.disputed
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{contractId}/arbitrate")
    public ResponseEntity<?> arbitrate(@PathVariable String contractId,
                                        @RequestBody Object request) {
        // TODO: admin arbitrates → emit contract.arbitrated
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<?> getContract(@PathVariable String contractId) {
        // TODO
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<?> listContracts() {
        // TODO: filter by buyerId / sellerId / status
        return ResponseEntity.ok().build();
    }
}
