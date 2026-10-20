package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.ContractId;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ReservationStatus;
import lombok.Getter;

import java.math.BigDecimal;

// Aggregate Root — identified by contractId (one reservation per contract)
// State machine: RESERVED → SIGNED_RELEASABLE → COMMITTED; RESERVED | SIGNED_RELEASABLE → RELEASED
@Getter
public class Reservation {

    private ContractId contractId;
    private ListingId listingId;
    private BigDecimal reservedQuantity;
    private ReservationStatus status;

    private Reservation() {
    }

    public static Reservation create(ContractId contractId, ListingId listingId, BigDecimal reservedQuantity) {
        if (contractId == null) throw new IllegalArgumentException("contractId must not be null");
        if (listingId == null) throw new IllegalArgumentException("listingId must not be null");
        if (reservedQuantity == null || reservedQuantity.signum() <= 0)
            throw new IllegalArgumentException("reservedQuantity must be positive");

        Reservation reservation = new Reservation();
        reservation.contractId = contractId;
        reservation.listingId = listingId;
        reservation.reservedQuantity = reservedQuantity;
        reservation.status = ReservationStatus.RESERVED;
        return reservation;
    }

    public static Reservation reconstruct(ContractId contractId, ListingId listingId,
                                          BigDecimal reservedQuantity, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.contractId = contractId;
        reservation.listingId = listingId;
        reservation.reservedQuantity = reservedQuantity;
        reservation.status = status;
        return reservation;
    }

    /**
     * Replay of the same reserve command returns the existing reservation instead of decrementing twice;
     * same contractId with a different payload is a conflict.
     */
    public boolean isSameRequest(ListingId listingId, BigDecimal requestedQuantity) {
        return this.listingId.equals(listingId) && this.reservedQuantity.compareTo(requestedQuantity) == 0;
    }
}
