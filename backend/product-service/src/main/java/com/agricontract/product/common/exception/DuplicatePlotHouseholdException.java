package com.agricontract.product.common.exception;

/**
 * Insert of a plot whose (sellerId, householdLabel) already exists: a concurrent import of the same household
 * committed first. Only the unique key violation maps here; any other integrity error stays as it is.
 */
public class DuplicatePlotHouseholdException extends RuntimeException {
    public DuplicatePlotHouseholdException(String householdLabel, Throwable cause) {
        super("Plot already registered for household: " + householdLabel, cause);
    }
}
