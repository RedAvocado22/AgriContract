package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

// Not VERIFIED, authorization expired, reputation-locked or no profile: 403 (user-service design §3).
public class ActorNotEligibleException extends ApiException {
    public ActorNotEligibleException() {
        super(HttpStatus.FORBIDDEN, "ACTOR_NOT_ELIGIBLE", "Account is not eligible for this action");
    }
}
