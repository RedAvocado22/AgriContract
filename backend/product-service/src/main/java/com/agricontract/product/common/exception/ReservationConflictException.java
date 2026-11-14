package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 409 carrying a frozen error-catalog code so contract-service can tell
 * INSUFFICIENT_LISTING_QUANTITY / LISTING_VERSION_MISMATCH / state conflicts apart.
 */
public class ReservationConflictException extends ApiException {

    public static final String INSUFFICIENT_LISTING_QUANTITY = "INSUFFICIENT_LISTING_QUANTITY";
    public static final String LISTING_VERSION_MISMATCH = "LISTING_VERSION_MISMATCH";
    public static final String LISTING_STATE_CONFLICT = "LISTING_STATE_CONFLICT";
    public static final String RESERVATION_STATE_CONFLICT = "RESERVATION_STATE_CONFLICT";

    public ReservationConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
