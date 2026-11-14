package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

public class UserProfileNotFoundException extends ApiException {
    public UserProfileNotFoundException(String userId) {
        super(HttpStatus.CONFLICT, "USER_PROFILE_NOT_FOUND", "User profile not found, register first: " + userId);
    }
}
