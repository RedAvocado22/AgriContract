package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.dto.*;
import com.agricontract.contract.application.usecase.*;
import com.agricontract.contract.common.ApiResponse;
import com.agricontract.contract.common.PaginatedResponse;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import com.agricontract.contract.infrastructure.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final CreateContractUseCase createContractUseCase;
    private final NegotiateContractUseCase negotiateContractUseCase;
    private final GetContractUseCase getContractUseCase;
    private final ListContractsUseCase listContractsUseCase;
    private final UpdateContractTermsUseCase updateContractTermsUseCase;
    private final JcsTermsCanonicalizer termsReader;

    /** Revise coffee terms before the first signature (#93). Body is ContractTermsInput, read strictly. */
    @PatchMapping(path = "/{contractId}/terms", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Revise coffee contract terms before the first signature")
    public ResponseEntity<CoffeeContractResponse> updateTerms(
            @PathVariable UUID contractId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody String body,
            Authentication auth) {
        boolean buyerOrSeller = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_BUYER") || role.equals("ROLE_SELLER"));
        if (!buyerOrSeller) {
            throw new UnauthorizedContractActionException("Only BUYER or SELLER can revise contract terms.");
        }
        CoffeeContractView view = updateContractTermsUseCase.execute(new UpdateContractTermsCommand(
                contractId.toString(), auth.getName(), idempotencyKey, termsReader.readInput(body)));
        return ResponseEntity.ok(CoffeeContractResponse.from(view));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ContractResponse>> offerContract(
            @Valid @RequestBody CreateContractRequest request,
            Authentication auth) {
        String buyerId = auth.getName();
        ContractResponse response = createContractUseCase.execute(new CreateContractCommand(
                request.contractId(), buyerId, request.listingId(), request.terms()
        ));
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @PutMapping("/{contractId}/negotiate")
    public ResponseEntity<ApiResponse<ContractResponse>> negotiate(
            @PathVariable String contractId,
            @Valid @RequestBody NegotiateContractRequest request,
            Authentication auth) {
        String userId = auth.getName();
        ContractResponse response = negotiateContractUseCase.execute(
                new NegotiateContractCommand(contractId, userId, request.newTerms())
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ApiResponse<ContractResponse>> getContract(
            @PathVariable String contractId,
            Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        ContractResponse response = getContractUseCase.execute(contractId, auth.getName(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "List contracts for the authenticated user with optional status filter and pagination")
    public ResponseEntity<ApiResponse<PaginatedResponse<ContractResponse>>> listContracts(
            @Parameter(description = "BUYER (default) or SELLER")
            @ModelAttribute @Valid ListContractsRequest request,
            Authentication auth) {
        String userId = auth.getName();
        PagedResult<ContractResponse> result = "SELLER".equalsIgnoreCase(request.getRole())
                ? listContractsUseCase.executeBySeller(userId, request.getStatus(), request.toPageable())
                : listContractsUseCase.executeByBuyer(userId, request.getStatus(), request.toPageable());
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.from(result)));
    }

}
