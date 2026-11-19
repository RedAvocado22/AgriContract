package com.agricontract.user.application.port;

import java.time.Instant;
import java.util.List;

/**
 * The login accounts, kept in Keycloak (D59). user-service's own record of a verified email is the source of truth;
 * Keycloak's emailVerified flag is a copy. Every method throws IdentityProviderUnavailableException when Keycloak
 * cannot be reached or refuses the call.
 */
public interface IdentityAccounts {

    // Self-registered accounts (realm role BUYER or SELLER) that Keycloak still marks unverified and that were created
    // before cutoff. Staff accounts and accounts without a creation time (imported seed) are never returned.
    List<String> unverifiedSelfRegisteredCreatedBefore(Instant cutoff);

    void markEmailVerified(String userId);

    // Deleting an account that no longer exists does nothing.
    void delete(String userId);
}
