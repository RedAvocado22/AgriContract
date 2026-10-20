package com.agricontract.product.common.exception;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String contractId) {
        super("Reservation for contract " + contractId + " not found");
    }
}
