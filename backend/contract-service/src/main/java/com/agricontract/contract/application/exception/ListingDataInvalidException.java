package com.agricontract.contract.application.exception;

/**
 * product-service answered, but its body does not fit the frozen schema (#430). Retrying cannot help, so this is
 * an internal error (500), not {@link ListingServiceUnavailableException} (503). The message names the listing and
 * the bad field for the log; it is never sent to the client.
 */
public class ListingDataInvalidException extends RuntimeException {
    public ListingDataInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
