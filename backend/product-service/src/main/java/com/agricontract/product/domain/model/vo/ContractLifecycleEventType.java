package com.agricontract.product.domain.model.vo;

// Contract lifecycle events product-service consumes; the wire name is the envelope eventType.
public enum ContractLifecycleEventType {
    CONTRACT_CREATED("contract.created"),
    /** RESERVED -> SIGNED_RELEASABLE: signed is not the inventory commit point. */
    CONTRACT_SIGNED("contract.signed"),
    /** SIGNED_RELEASABLE -> COMMITTED: the hard commit point. */
    CONTRACT_ACTIVATED("contract.activated"),
    /** RESERVED | SIGNED_RELEASABLE -> RELEASED, quantity restored. */
    CONTRACT_ACTIVATION_FAILED("contract.activation_failed"),
    /** RESERVED -> RELEASED before the second signature, quantity restored. */
    CONTRACT_WITHDRAWN("contract.withdrawn");

    private final String wireName;

    ContractLifecycleEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
