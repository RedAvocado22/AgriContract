package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.application.dto.ActivationRecoveryView;
import com.agricontract.contract.application.dto.CoffeeContractView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.util.List;

/**
 * ContractResponse of golden-flow-api.yaml, without the Phase 1 ApiResponse wrapper (D32). terms is written as the
 * stored canonical JSON so the client sees exactly the bytes behind signedContentHash. Milestones arrive with #119.
 */
public record CoffeeContractResponse(
        String contractId,
        String listingId,
        BigDecimal reservedQuantity,
        String buyerId,
        String sellerId,
        String status,
        @JsonRawValue String terms,
        String signedContentHash,
        List<Object> milestones,
        @JsonInclude(JsonInclude.Include.NON_NULL) ActivationRecovery activationRecovery) {

    /** Present only after escrow.deposit_lock_failed (golden-flow-api ContractResponse.activationRecovery, D42). */
    public record ActivationRecovery(String depositLockFailedAt, String oneSidedCancelAllowedFrom,
                                     List<String> cancelRequestedBy) {
    }

    public static CoffeeContractResponse from(CoffeeContractView view) {
        ActivationRecoveryView recovery = view.activationRecovery();
        return new CoffeeContractResponse(view.contractId(), view.listingId(), view.reservedQuantity(),
                view.buyerId(), view.sellerId(), view.status().name(), view.termsJson(), view.signedContentHash(),
                List.of(), recovery == null ? null : new ActivationRecovery(
                        recovery.depositLockFailedAt().toString(), recovery.oneSidedCancelAllowedFrom().toString(),
                        recovery.cancelRequestedBy()));
    }
}
