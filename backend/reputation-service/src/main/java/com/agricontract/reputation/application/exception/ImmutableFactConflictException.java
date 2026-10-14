package com.agricontract.reputation.application.exception;
public class ImmutableFactConflictException extends RuntimeException {
    public ImmutableFactConflictException() { super("Conflicting immutable reputation fact"); }
}
