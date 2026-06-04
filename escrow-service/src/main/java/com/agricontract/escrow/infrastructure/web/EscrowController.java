package com.agricontract.escrow.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/escrows")
@RequiredArgsConstructor
public class EscrowController {

    @GetMapping("/contract/{contractId}")
    public ResponseEntity<?> getByContractId(@PathVariable String contractId) {
        // TODO
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{escrowId}/transactions")
    public ResponseEntity<?> getTransactions(@PathVariable String escrowId) {
        // TODO
        return ResponseEntity.ok().build();
    }
}
