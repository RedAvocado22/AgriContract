package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.dto.RetryDepositLockCommand;
import com.agricontract.contract.application.dto.ActivationCancelCommands;
import com.agricontract.contract.application.usecase.ActivationCancelUseCases;
import com.agricontract.contract.infrastructure.config.GatewayAuthDetails;
import com.agricontract.contract.infrastructure.web.dto.InitiateActivationCancelRequest;
import com.agricontract.contract.infrastructure.web.dto.InitiateSignResponse;
import com.agricontract.contract.infrastructure.web.dto.VerifyOtpAndSignRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.time.Instant;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.application.usecase.RetryDepositLockUseCase;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Activation recovery after escrow.deposit_lock_failed (D42): OPERATOR retries the lock; the parties request (and may
 * withdraw) the cancel, with the signing tier of consent (checkbox + fresh login + OTP).
 */
@RestController
@RequestMapping("/api/v1/contracts")
public class ActivationRecoveryController {

    private final RetryDepositLockUseCase retryDepositLock;
    private final ActivationCancelUseCases cancel;
    private final ObjectReader initiateReader;
    private final ObjectReader verifyReader;

    public ActivationRecoveryController(RetryDepositLockUseCase retryDepositLock, ActivationCancelUseCases cancel,
                                        ObjectMapper objectMapper) {
        this.retryDepositLock = retryDepositLock;
        this.cancel = cancel;
        this.initiateReader = objectMapper.readerFor(InitiateActivationCancelRequest.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.verifyReader = objectMapper.readerFor(VerifyOtpAndSignRequest.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

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

    /** A party asks to stop the contract: consent + OTP challenge + OTP email. No request exists until /verify. */
    @PostMapping(path = "/{contractId}/activation/cancel/initiate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Party: start a cancel request (checkbox + fresh login + emailed code)")
    public ResponseEntity<InitiateSignResponse> initiateCancel(
            @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody String body,
            Authentication auth) {
        requireParty(auth);
        InitiateActivationCancelRequest request = read(initiateReader, body, "InitiateActivationCancelRequest");
        var view = cancel.initiate(new ActivationCancelCommands.Initiate(contractId.toString(), auth.getName(),
                idempotencyKey, request.requesterRole(), request.consentStatementVersion(),
                request.presentedContentHash(), authTime(auth)));
        return ResponseEntity.ok(InitiateSignResponse.from(view));
    }

    /** Checks the code and records the request; the refund starts at once when the D42 conditions hold. */
    @PostMapping(path = "/{contractId}/activation/cancel/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Party: confirm the cancel request with the emailed code")
    public ResponseEntity<Void> verifyCancel(
            @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody String body,
            Authentication auth) {
        requireParty(auth);
        VerifyOtpAndSignRequest request = read(verifyReader, body, "VerifyOtpAndSignRequest");
        cancel.verify(new ActivationCancelCommands.Verify(contractId.toString(), auth.getName(), idempotencyKey,
                request.otpId(), request.otpCode(), authTime(auth)));
        return ResponseEntity.noContent().build();
    }

    /** Withdraws the caller's own standing request while the contract is still SIGNED. */
    @DeleteMapping("/{contractId}/activation/cancel")
    @Operation(summary = "Party: withdraw your cancel request before the refund starts")
    public ResponseEntity<Void> withdrawCancel(
            @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Authentication auth) {
        requireParty(auth);
        cancel.withdraw(new ActivationCancelCommands.Withdraw(contractId.toString(), auth.getName(), idempotencyKey));
        return ResponseEntity.noContent().build();
    }

    private static void requireParty(Authentication auth) {
        boolean party = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_BUYER") || role.equals("ROLE_SELLER"));
        if (!party) {
            throw new UnauthorizedContractActionException("Only BUYER or SELLER can cancel a contract.");
        }
    }

    private static Instant authTime(Authentication auth) {
        return auth.getDetails() instanceof GatewayAuthDetails details ? details.authTime() : null;
    }

    /** Never echoes the body: a verify body holds the code. */
    private static <T> T read(ObjectReader reader, String body, String type) {
        try {
            return reader.readValue(body);
        } catch (JsonProcessingException unreadable) {
            if (unreadable.getCause() instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Body is not a valid " + type + ": "
                    + unreadable.getOriginalMessage(), unreadable);
        }
    }
}
