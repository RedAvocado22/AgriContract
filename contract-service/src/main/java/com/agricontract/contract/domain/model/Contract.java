package com.agricontract.contract.domain.model;

import com.agricontract.contract.domain.event.DomainEvent;
import com.agricontract.contract.domain.model.vo.*;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// Aggregate Root — most complex service
// State machine:
//   OFFERED → SIGNED | CANCELLED_BY_BUYER | CANCELLED_BY_SELLER
//   SIGNED  → GOODS_DELIVERED | DISPUTED
//   GOODS_DELIVERED → SETTLED
//   DISPUTED → ARBITRATED
@Getter
public class Contract {

    private ContractId contractId;
    private String listingId;
    private String sellerId;
    private String buyerId;
    private String productName;       // snapshot at offer time
    private Quantity quantity;        // snapshot
    private Money agreedPrice;        // snapshot
    private LocalDate deliveryDeadline;
    private ContractStatus status;
    private String cancelReason;
    private Money penaltyAmount;

    private Contract() {}

    public static Contract offer(ContractId contractId, String listingId,
                                  String buyerId, String sellerId,
                                  String productName, Quantity quantity,
                                  Money agreedPrice, LocalDate deliveryDeadline) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /** Seller signs → SIGNED. Triggers: close listing (Feign) + lock escrow (event) */
    public void sign(String actorId) { /* TODO */ }

    /** Buyer withdraws offer → CANCELLED_BY_BUYER */
    public void cancelByBuyer(String actorId, String reason) { /* TODO */ }

    /** Seller rejects offer → CANCELLED_BY_SELLER */
    public void cancelBySeller(String actorId, String reason) { /* TODO */ }

    /** Seller confirms goods shipped → GOODS_DELIVERED */
    public void confirmDelivery(String actorId) { /* TODO */ }

    /** Buyer confirms receipt → SETTLED. Triggers: release escrow (event) */
    public void settle(String actorId) { /* TODO */ }

    /** Buyer opens dispute → DISPUTED */
    public void dispute(String actorId, String reason) { /* TODO */ }

    /** Admin arbitrates → ARBITRATED. Triggers: penalty escrow (event) */
    public void arbitrate(Money penalty, boolean penalizeBuyer) { /* TODO */ }

    /** Drain domain events for Outbox Poller to publish to RabbitMQ */
    public List<DomainEvent> pullDomainEvents() {
        // TODO
        return List.of();
    }
}
