package com.agricontract.user.application.exception;

// Keycloak could not be reached or refused the call. Never carries tokens, secrets or response bodies.
public class IdentityProviderUnavailableException extends RuntimeException {
    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
