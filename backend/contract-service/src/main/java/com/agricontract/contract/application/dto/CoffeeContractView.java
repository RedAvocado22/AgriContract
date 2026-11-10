package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractStatus;

import java.math.BigDecimal;

/** Phase 2 contract read model. termsJson is the stored canonical JSON, returned verbatim. */
public record CoffeeContractView(
        String contractId,
        String listingId,
        BigDecimal reservedQuantity,
        String buyerId,
        String sellerId,
        ContractStatus status,
        String termsJson,
        String signedContentHash,
        ActivationRecoveryView activationRecovery) {

    /** Without activation recovery: every contract whose deposit lock has not failed. */
    public CoffeeContractView(String contractId, String listingId, BigDecimal reservedQuantity, String buyerId,
                              String sellerId, ContractStatus status, String termsJson, String signedContentHash) {
        this(contractId, listingId, reservedQuantity, buyerId, sellerId, status, termsJson, signedContentHash, null);
    }

    public CoffeeContractView withActivationRecovery(ActivationRecoveryView recovery) {
        return new CoffeeContractView(contractId, listingId, reservedQuantity, buyerId, sellerId, status, termsJson,
                signedContentHash, recovery);
    }

    public static CoffeeContractView from(Contract contract) {
        var terms = contract.getCoffeeTerms();
        return new CoffeeContractView(contract.getContractId().value(), contract.getListingId(),
                contract.getReservedQuantity(), contract.getBuyerId(), contract.getSellerId(), contract.getStatus(),
                terms.canonicalJson(), terms.signedContentHash());
    }
}
