package com.agricontract.contract.application.dto;

/**
 * GET /contracts/{id}: a coffee contract (#92 onwards) is read as the frozen ContractResponse, from {@code coffee}.
 * {@code legacy} is set only for Phase 1 rows created before #92, which have no coffee terms or signedContentHash;
 * it goes away with the Phase 1 cleanup. Exactly one of the two is non-null.
 */
public record ContractDetail(CoffeeContractView coffee, ContractResponse legacy) {

    public boolean isCoffee() {
        return coffee != null;
    }
}
