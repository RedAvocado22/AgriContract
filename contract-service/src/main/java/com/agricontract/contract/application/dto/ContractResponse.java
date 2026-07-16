package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractTerms;

import java.util.Set;

public record ContractResponse(
        String contractId,
        String listingId,
        String buyerId,
        String sellerId,
        String productName,
        String buyerOrgName,
        String sellerOrgName,
        String buyerEmail,
        String sellerEmail,
        ContractTerms terms,
        String status,
        String cancelReason,
        String cancelledBy,
        int termsRevision,
        Set<String> signatories
) {
    public static ContractResponse from(Contract contract) {
        return new ContractResponse(
                contract.getContractId().value(),
                contract.getListingId(),
                contract.getBuyerId(),
                contract.getSellerId(),
                contract.getProductName(),
                contract.getBuyerOrgName(),
                contract.getSellerOrgName(),
                contract.getBuyerEmail(),
                contract.getSellerEmail(),
                contract.getTerms(),
                contract.getStatus().name(),
                contract.getCancelReason(),
                contract.getCancelledBy() != null ? contract.getCancelledBy().name() : null,
                contract.getTermsRevision(),
                contract.getSignatories()
        );
    }
}
