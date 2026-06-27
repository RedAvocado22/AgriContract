package com.agricontract.contract.infrastructure.web;

import com.agricontract.contract.application.dto.*;
import com.agricontract.contract.application.usecase.*;
import com.agricontract.contract.common.ApiResponse;
import com.agricontract.contract.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final ListContractsUseCase listContractsUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<ContractResponse>> offerContract(
            @RequestBody CreateContractRequest request,
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
            @RequestBody NegotiateContractRequest request,
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContractResponse>>> listContracts(
            @RequestParam(required = false) String role,
            Authentication auth) {
        String userId = auth.getName();
        List<ContractResponse> contracts = "SELLER".equalsIgnoreCase(role)
                ? listContractsUseCase.executeBySeller(userId)
                : listContractsUseCase.executeByBuyer(userId);
        return ResponseEntity.ok(ApiResponse.ok(contracts));
    }
}
