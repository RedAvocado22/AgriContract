package com.agricontract.user.infrastructure.web.dto;

import java.time.Instant;

// UTC instant from the real authorization document; null only when the document has no expiry.
public record VerifyUserRequest(Instant authorizationExpiresAt) {
}
