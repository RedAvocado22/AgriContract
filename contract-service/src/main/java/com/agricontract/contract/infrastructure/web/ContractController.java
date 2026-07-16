package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.dto.*;
import com.agricontract.contract.application.usecase.*;
import com.agricontract.contract.common.ApiResponse;
import com.agricontract.contract.common.PaginatedResponse;
import com.agricontract.contract.infrastructure.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final CreateContractUseCase createContractUseCase;
    private final NegotiateContractUseCase negotiateContractUseCase;
    private final SignContractUseCase signContractUseCase;
    private final CancelContractUseCase cancelContractUseCase;
    private final ConfirmDeliveryUseCase confirmDeliveryUseCase;
    private final DisputeContractUseCase disputeContractUseCase;
    private final GetContractUseCase getContractUseCase;
    private final GetNegotiationHistoryUseCase getNegotiationHistoryUseCase;
    private final ListContractsUseCase listContractsUseCase;

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

    @PutMapping("/{contractId}/sign")
    public ResponseEntity<ApiResponse<Void>> sign(
            @PathVariable String contractId,
            Authentication auth) {
        signContractUseCase.execute(new SignContractCommand(contractId, auth.getName()));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{contractId}/cancel")
    public ResponseEntity<ApiResponse<ContractResponse>> cancel(
            @PathVariable String contractId,
            @RequestBody CancelContractRequest request,
            Authentication auth) {
        ContractResponse response = cancelContractUseCase.execute(
                new CancelContractCommand(contractId, auth.getName(), request.reason())
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{contractId}/confirm-delivery")
    public ResponseEntity<ApiResponse<ContractResponse>> confirmDelivery(
            @PathVariable String contractId,
            Authentication auth) {
        ContractResponse response = confirmDeliveryUseCase.execute(
                new ConfirmDeliveryCommand(contractId, auth.getName())
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{contractId}/dispute")
    public ResponseEntity<ApiResponse<ContractResponse>> dispute(
            @PathVariable String contractId,
            @RequestBody DisputeContractRequest request,
            Authentication auth) {
        ContractResponse response = disputeContractUseCase.execute(
                new DisputeContractCommand(contractId, auth.getName(), request.reason())
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

    @GetMapping("/{contractId}/negotiations")
    public ResponseEntity<ApiResponse<java.util.List<NegotiationHistoryResponse>>> getNegotiationHistory(
            @PathVariable String contractId,
            Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        var response = getNegotiationHistoryUseCase.execute(contractId, auth.getName(), isAdmin);
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
