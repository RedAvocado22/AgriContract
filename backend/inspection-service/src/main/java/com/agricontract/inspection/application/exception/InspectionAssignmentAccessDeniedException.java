package com.agricontract.inspection.application.exception;

public class InspectionAssignmentAccessDeniedException extends RuntimeException {
    public InspectionAssignmentAccessDeniedException() {
        super("No authorized inspection assignment");
    }
}
