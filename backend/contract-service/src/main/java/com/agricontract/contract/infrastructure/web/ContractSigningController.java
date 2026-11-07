package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.dto.InitiateSignCommand;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.application.usecase.InitiateSignUseCase;
import com.agricontract.contract.infrastructure.config.GatewayAuthDetails;
import com.agricontract.contract.infrastructure.web.dto.InitiateSignRequest;
import com.agricontract.contract.infrastructure.web.dto.InitiateSignResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
public class ContractSigningController {
    private final InitiateSignUseCase initiateSignUseCase;
    private final ObjectReader initiateReader;

    public ContractSigningController(InitiateSignUseCase initiateSignUseCase, ObjectMapper objectMapper) {
        this.initiateSignUseCase = initiateSignUseCase;
        this.initiateReader = objectMapper.readerFor(InitiateSignRequest.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Checkbox consent + OTP challenge + OTP email (#96). No signature exists until /sign/verify. */
    @PostMapping(path = "/{contractId}/sign/initiate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start signing: record the checkbox consent and email a one-time code")
    public ResponseEntity<InitiateSignResponse> initiate(
            @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody String body,
            Authentication auth) {
        boolean buyerOrSeller = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_BUYER") || role.equals("ROLE_SELLER"));
        if (!buyerOrSeller) {
            throw new UnauthorizedContractActionException("Only BUYER or SELLER can sign a contract.");
        }
        InitiateSignRequest request = read(body);
        Instant authTime = auth.getDetails() instanceof GatewayAuthDetails details ? details.authTime() : null;
        var view = initiateSignUseCase.execute(new InitiateSignCommand(contractId.toString(), auth.getName(),
                idempotencyKey, request.signerRole(), request.consentStatementVersion(),
                request.presentedContentHash(), authTime));
        return ResponseEntity.ok(InitiateSignResponse.from(view));
    }

    private InitiateSignRequest read(String body) {
        try {
            return initiateReader.readValue(body);
        } catch (JsonProcessingException unreadable) {
            Throwable cause = unreadable.getCause();
            if (cause instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Body is not a valid InitiateSignRequest: "
                    + unreadable.getOriginalMessage(), unreadable);
        }
    }
}
