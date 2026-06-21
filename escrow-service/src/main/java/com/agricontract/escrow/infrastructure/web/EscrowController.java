package com.agricontract.escrow.infrastructure.web;

import com.agricontract.escrow.application.dto.ArbitrateEscrowCommand;
import com.agricontract.escrow.application.dto.ConfirmDepositCommand;
import com.agricontract.escrow.application.dto.EscrowAccountResponse;
import com.agricontract.escrow.application.dto.EscrowTransactionResponse;
import com.agricontract.escrow.application.usecase.ArbitrateEscrowUseCase;
import com.agricontract.escrow.application.usecase.ConfirmDepositUseCase;
import com.agricontract.escrow.application.usecase.GetEscrowByContractIdUseCase;
import com.agricontract.escrow.application.usecase.GetEscrowTransactionsUseCase;
import com.agricontract.escrow.common.ApiResponse;
import com.agricontract.escrow.infrastructure.web.dto.ArbitrateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/escrows")
@RequiredArgsConstructor
public class EscrowController {

    private final ConfirmDepositUseCase confirmDepositUseCase;
    private final ArbitrateEscrowUseCase arbitrateEscrowUseCase;
    private final GetEscrowByContractIdUseCase getEscrowByContractIdUseCase;
    private final GetEscrowTransactionsUseCase getEscrowTransactionsUseCase;

    @GetMapping("/contract/{contractId}")
    public ResponseEntity<ApiResponse<EscrowAccountResponse>> getByContractId(@PathVariable String contractId) {
        return ResponseEntity.ok(ApiResponse.ok(getEscrowByContractIdUseCase.execute(contractId)));
    }

    @GetMapping("/{escrowId}/transactions")
    public ResponseEntity<ApiResponse<List<EscrowTransactionResponse>>> getTransactions(@PathVariable String escrowId) {
        return ResponseEntity.ok(ApiResponse.ok(getEscrowTransactionsUseCase.execute(escrowId)));
    }

    @PutMapping("/{contractId}/confirm-deposit")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> confirmDeposit(
            @PathVariable String contractId,
            @AuthenticationPrincipal String sellerUserId
    ) {
        ConfirmDepositCommand command = new ConfirmDepositCommand(contractId, sellerUserId);
        confirmDepositUseCase.execute(command);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{contractId}/arbitrate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> arbitrate(
            @PathVariable String contractId,
            @Valid @RequestBody ArbitrateRequest request
    ) {
        ArbitrateEscrowCommand command = new ArbitrateEscrowCommand(
                contractId, request.buyerAmount(), request.sellerAmount(), request.justification()
        );
        arbitrateEscrowUseCase.execute(command);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
