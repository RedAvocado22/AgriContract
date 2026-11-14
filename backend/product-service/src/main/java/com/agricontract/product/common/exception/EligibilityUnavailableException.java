package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

// user-service unreachable or its answer unusable: the gate fails closed with 503 (matrix 21f).
public class EligibilityUnavailableException extends ApiException {
    public EligibilityUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "ELIGIBILITY_UNAVAILABLE", "Eligibility check is temporarily unavailable");
    }
}
