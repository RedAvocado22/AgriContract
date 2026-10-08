package com.agricontract.inspection.application.port;

public interface AuthenticatedInspectorPort {
    /** Returns identity from an established authenticated context, never from an operation DTO. */
    String requireInspectorId();
}
