package com.agricontract.inspection.domain.exception;

/** Well-formed commitment differs from the digest of the supplied material. */
public class InspectionCommitmentMismatchException extends RuntimeException {
    public InspectionCommitmentMismatchException() {
        super("Inspection commitment mismatch");
    }
}
