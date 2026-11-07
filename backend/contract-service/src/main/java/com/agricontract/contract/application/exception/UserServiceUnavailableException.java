package com.agricontract.contract.application.exception;

/** user-service could not answer; a check that depends on it must refuse rather than let the action through. */
public class UserServiceUnavailableException extends RuntimeException {
    public UserServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
