package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 409: the seller already used this Idempotency-Key for a different request. Same code as contract-service, so a
 * client handles both the same way: send the new request with a new key.
 */
public class IdempotencyKeyReusedException extends ApiException {
    public IdempotencyKeyReusedException() {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "This Idempotency-Key was already used for a different request; send a new key.");
    }
}
