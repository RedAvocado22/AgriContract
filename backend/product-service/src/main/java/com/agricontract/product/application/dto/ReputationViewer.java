package com.agricontract.product.application.dto;

import java.util.List;
import java.util.Objects;

/**
 * The signed-in user a reputation lookup is made for. The public summary is authenticated-only
 * (reputation design §6.1b), so product-service asks on behalf of the viewer, never as itself.
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
