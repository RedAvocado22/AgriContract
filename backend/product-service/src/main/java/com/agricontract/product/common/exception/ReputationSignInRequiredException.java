package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

public class ReputationSignInRequiredException extends ApiException {
    public ReputationSignInRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "SIGN_IN_REQUIRED", "Sign in to filter listings by seller reputation");
    }
}
