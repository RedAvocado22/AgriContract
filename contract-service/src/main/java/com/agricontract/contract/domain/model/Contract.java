package com.agricontract.contract.domain.model;

import com.agricontract.contract.domain.event.DomainEvent;
import com.agricontract.contract.domain.model.vo.*;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// Aggregate Root — service phức tạp nhất
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
    private String productName;       // snapshot tại thời điểm offer
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

    /** Seller ký → SIGNED. Trigger: close listing (Feign) + lock escrow (event) */
    public void sign(String actorId) { /* TODO */ }

    /** Buyer rút offer → CANCELLED_BY_BUYER */
    public void cancelByBuyer(String actorId, String reason) { /* TODO */ }

    /** Seller từ chối → CANCELLED_BY_SELLER */
    public void cancelBySeller(String actorId, String reason) { /* TODO */ }

    /** Seller xác nhận đã giao hàng → GOODS_DELIVERED */
    public void confirmDelivery(String actorId) { /* TODO */ }

    /** Buyer xác nhận nhận hàng → SETTLED. Trigger: release escrow (event) */
    public void settle(String actorId) { /* TODO */ }

    /** Buyer mở tranh chấp → DISPUTED */
    public void dispute(String actorId, String reason) { /* TODO */ }

    /** Admin phân xử → ARBITRATED. Trigger: penalty escrow (event) */
    public void arbitrate(Money penalty, boolean penalizeBuyer) { /* TODO */ }

    /** Drain domain events cho Outbox Poller publish lên RabbitMQ */
    public List<DomainEvent> pullDomainEvents() {
        // TODO
        return List.of();
    }
}
