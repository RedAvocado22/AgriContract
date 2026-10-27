package com.agricontract.product.domain.model.vo;

// Contract lifecycle events product-service consumes; the wire name is the envelope eventType.
public enum ContractLifecycleEventType {
    CONTRACT_CREATED("contract.created");

    private final String wireName;

    ContractLifecycleEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
