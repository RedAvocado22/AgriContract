package com.agricontract.contract.application.exception;

/** product-service could not answer; a contract create must refuse rather than persist an unreserved offer. */
public class ListingServiceUnavailableException extends RuntimeException {
    public ListingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
