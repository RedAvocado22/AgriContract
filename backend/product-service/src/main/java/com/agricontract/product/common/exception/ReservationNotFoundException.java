package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

public class ReservationNotFoundException extends ApiException {
    public ReservationNotFoundException(String contractId) {
        super(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation for contract " + contractId + " not found");
    }
}
