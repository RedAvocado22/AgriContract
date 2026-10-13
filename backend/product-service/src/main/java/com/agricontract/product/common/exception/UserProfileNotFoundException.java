package com.agricontract.product.common.exception;

public class UserProfileNotFoundException extends RuntimeException {
    public UserProfileNotFoundException(String userId) {
        super("User profile not found, register first: " + userId);
    }
}
