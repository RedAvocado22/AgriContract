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
        String signedContentHash) {

    public static CoffeeContractView from(Contract contract) {
        var terms = contract.getCoffeeTerms();
        return new CoffeeContractView(contract.getContractId().value(), contract.getListingId(),
                terms.snapshot().totalQuantity(), contract.getBuyerId(), contract.getSellerId(), contract.getStatus(),
                terms.canonicalJson(), terms.signedContentHash());
    }
}
