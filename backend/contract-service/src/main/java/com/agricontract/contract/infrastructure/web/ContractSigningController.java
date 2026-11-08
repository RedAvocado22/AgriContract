package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.dto.InitiateSignCommand;
import com.agricontract.contract.application.dto.VerifySignCommand;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.application.usecase.InitiateSignUseCase;
import com.agricontract.contract.application.usecase.VerifyOtpAndSignUseCase;
import com.agricontract.contract.infrastructure.config.GatewayAuthDetails;
import com.agricontract.contract.infrastructure.web.dto.InitiateSignRequest;
import com.agricontract.contract.infrastructure.web.dto.InitiateSignResponse;
import com.agricontract.contract.infrastructure.web.dto.VerifyOtpAndSignRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
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
    private final VerifyOtpAndSignUseCase verifyUseCase;
    private final ClientIpResolver clientIp;
    private final ObjectReader initiateReader;
    private final ObjectReader verifyReader;

    public ContractSigningController(InitiateSignUseCase initiateSignUseCase, VerifyOtpAndSignUseCase verifyUseCase,
                                     ClientIpResolver clientIp, ObjectMapper objectMapper) {
        this.initiateSignUseCase = initiateSignUseCase;
        this.verifyUseCase = verifyUseCase;
        this.clientIp = clientIp;
        this.initiateReader = objectMapper.readerFor(InitiateSignRequest.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.verifyReader = objectMapper.readerFor(VerifyOtpAndSignRequest.class)
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
        requireBuyerOrSeller(auth);
        InitiateSignRequest request = read(initiateReader, body, "InitiateSignRequest");
        Instant authTime = authTime(auth);
        var view = initiateSignUseCase.execute(new InitiateSignCommand(contractId.toString(), auth.getName(),
                idempotencyKey, request.signerRole(), request.consentStatementVersion(),
                request.presentedContentHash(), authTime));
        return ResponseEntity.ok(InitiateSignResponse.from(view));
    }

    /** Checks the emailed code and records this party's signature; the second one makes the contract SIGNED (#97). */
    @PostMapping(path = "/{contractId}/sign/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sign: check the one-time code and record the signature")
    public ResponseEntity<Void> verify(
            @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody String body,
            HttpServletRequest httpRequest,
            Authentication auth) {
        requireBuyerOrSeller(auth);
        VerifyOtpAndSignRequest request = read(verifyReader, body, "VerifyOtpAndSignRequest");
        verifyUseCase.execute(new VerifySignCommand(contractId.toString(), auth.getName(), idempotencyKey,
                request.otpId(), request.otpCode(), authTime(auth), clientIp.resolve(httpRequest)));
        return ResponseEntity.noContent().build();
    }

    private static void requireBuyerOrSeller(Authentication auth) {
        boolean buyerOrSeller = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_BUYER") || role.equals("ROLE_SELLER"));
        if (!buyerOrSeller) {
            throw new UnauthorizedContractActionException("Only BUYER or SELLER can sign a contract.");
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
            Throwable cause = unreadable.getCause();
            if (cause instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Body is not a valid " + type + ": "
                    + unreadable.getOriginalMessage(), unreadable);
        }
    }
}
