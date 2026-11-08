package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.dto.RetryDepositLockCommand;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.application.usecase.RetryDepositLockUseCase;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Activation recovery after escrow.deposit_lock_failed (D42). The parties' cancel requests come with #234's next PR. */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ActivationRecoveryController {

    private final RetryDepositLockUseCase retryDepositLock;

    /** OPERATOR only, after fixing the cause (bank down, kill switch). 204: escrow retries the failed legs. */
    @PostMapping("/{contractId}/activation/retry-deposit-lock")
    @Operation(summary = "OPERATOR: lock the failed deposit legs again after fixing the cause")
    public ResponseEntity<Void> retryDepositLock(
            @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Authentication auth) {
        boolean operator = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OPERATOR"::equals);
        if (!operator) {
            throw new UnauthorizedContractActionException("Only OPERATOR can retry a deposit lock.");
        }
        retryDepositLock.execute(new RetryDepositLockCommand(contractId.toString(), auth.getName(), idempotencyKey));
        return ResponseEntity.noContent().build();
    }
}
