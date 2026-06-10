package com.agricontract.contract.domain.model;

import com.agricontract.contract.domain.model.vo.*;
import lombok.Getter;

import java.time.LocalDate;

// Aggregate Root
// State machine:
//   OFFERED → NEGOTIATING → SIGNED → ACTIVE → DELIVERED → SETTLED
//   ACTIVE  → CANCELLED
//   DELIVERED → DISPUTED
@Getter
public class Contract {

    private ContractId contractId;
    private String listingId;
    private String sellerId;
    private String buyerId;
    private String productName;       // snapshot at offer time
    private String buyerOrgName;      // snapshot at offer time
    private String sellerOrgName;     // snapshot at offer time
    private Quantity quantity;
    private Money agreedPrice;
    private LocalDate deliveryDeadline;
    private ContractStatus status;
    private String cancelReason;
    private String cancelledBy;       // "BUYER" or "SELLER"

    private Contract() {}

    public static Contract offer(ContractId contractId, String listingId,
                                  String buyerId, String sellerId,
                                  String productName, String buyerOrgName, String sellerOrgName,
                                  Quantity quantity, Money agreedPrice,
                                  LocalDate deliveryDeadline) {
        throw new UnsupportedOperationException("TODO");
    }

    public void counterOffer(String userId) {
        throw new UnsupportedOperationException("TODO");
    }

    public void sign(String userId) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Called when escrow.locked event received */
    public void activate() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Only BUYER can call, only from ACTIVE */
    public void confirmDelivery(String buyerId) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Called when escrow.released event received */
    public void settle() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Only from ACTIVE, both parties can cancel */
    public void cancel(String userId, String reason) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Only BUYER, only from DELIVERED */
    public void dispute(String buyerId, String reason) {
        throw new UnsupportedOperationException("TODO");
    }
}
