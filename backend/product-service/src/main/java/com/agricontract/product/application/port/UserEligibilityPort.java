package com.agricontract.product.application.port;

import com.agricontract.product.application.dto.UserEligibility;

import java.util.Optional;

// Reads the KYC/authorization/lock facts from user-service's internal API (user-service design §4.3).
// Empty = user-service has no profile for this id. Any other failure throws EligibilityUnavailableException.
public interface UserEligibilityPort {
    Optional<UserEligibility> load(String userId);
}
