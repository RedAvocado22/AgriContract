package com.agricontract.product.application.dto;

import java.util.List;
import java.util.Objects;

/**
 * The signed-in user searching listings. Only a signed-in viewer sees seller reputation (reputation design §6.2).
 * The viewer is never forwarded: product-service reads reputation-service as itself (internal batch endpoint).
 */
public record ReputationViewer(String userId, List<String> roles) {
    public ReputationViewer {
        Objects.requireNonNull(userId, "userId");
        roles = List.copyOf(roles);
        if (userId.isBlank() || roles.isEmpty()) {
            throw new IllegalArgumentException("a viewer needs an id and at least one role");
        }
    }
}
