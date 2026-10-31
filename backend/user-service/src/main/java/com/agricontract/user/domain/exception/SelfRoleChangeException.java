package com.agricontract.user.domain.exception;

public class SelfRoleChangeException extends RuntimeException {
    public SelfRoleChangeException(String userId) {
        super("Actor cannot change their own role: " + userId);
    }
}
