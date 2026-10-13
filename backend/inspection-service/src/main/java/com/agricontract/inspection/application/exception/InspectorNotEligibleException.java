package com.agricontract.inspection.application.exception;

public class InspectorNotEligibleException extends RuntimeException {
    public InspectorNotEligibleException() {
        super("Inspector is not eligible");
    }
}
