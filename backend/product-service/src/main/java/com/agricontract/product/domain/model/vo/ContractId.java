package com.agricontract.product.domain.model.vo;

public record ContractId(String value) {
    public ContractId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("ContractId must not be blank");
    }
}
