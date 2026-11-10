package com.agricontract.contract.infrastructure.web.dto;

import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.InvalidContractTermsException;

import java.util.UUID;

/**
 * golden-flow-api.yaml CreateContractRequest, read strictly: no unknown field, so neither goodsTerms, a quantity nor
 * a contractId can be sent. requestedQuantity is derived from terms.milestoneSchedule.
 */
public record CreateContractRequest(
        UUID listingId,
        String listingVersionToken,
        ContractTermsInput terms
) {
    public CreateContractRequest requireComplete() {
        if (listingId == null) {
            throw new InvalidContractTermsException("listingId is required");
        }
        if (listingVersionToken == null || listingVersionToken.isBlank()) {
            throw new InvalidContractTermsException("listingVersionToken is required");
        }
        if (terms == null) {
            throw new InvalidContractTermsException("terms is required");
        }
        return this;
    }
}
