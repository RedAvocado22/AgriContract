package com.agricontract.user.common.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String userId) {
        super("User with id " + userId + " already exists");
    }
}
