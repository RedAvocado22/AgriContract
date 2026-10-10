package com.agricontract.user.domain.exception;

public class ConcurrentProfileUpdateException extends RuntimeException {
    public ConcurrentProfileUpdateException(String userId) {
        super("Profile was modified concurrently, reload and retry: " + userId);
    }
}
