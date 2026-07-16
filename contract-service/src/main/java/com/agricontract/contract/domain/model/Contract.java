package com.agricontract.contract.domain.model;

import com.agricontract.contract.domain.event.*;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.vo.CancelledBy;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.model.vo.ContractTerms;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Aggregate Root
// State machine:
//   OFFERED → NEGOTIATING → SIGNED → ACTIVE → DELIVERED → SETTLED
//   ACTIVE  → CANCELLED
//   DELIVERED → DISPUTED → SETTLED
@Getter
public class Contract {

    private ContractId contractId;
    private String listingId;
    private String sellerId;
    private String buyerId;
    private String productName;       // snapshot at offer time
    private String buyerOrgName;      // snapshot at offer time
    private String sellerOrgName;     // snapshot at offer time
    private String buyerEmail;        // snapshot at offer time
    private String sellerEmail;       // snapshot at offer time
    private ContractTerms terms;
    private int termsRevision;
    private ContractStatus status;
    private String cancelReason;
    private CancelledBy cancelledBy;       // "BUYER" or "SELLER"
    @Getter(AccessLevel.NONE)
    private Set<String> signatories;
    @Getter(AccessLevel.NONE)
    private List<DomainEvent> domainEvents;

    private Contract() {
        this.signatories = new HashSet<>();
        this.domainEvents = new ArrayList<>();
    }

    public static Contract reconstitute(
            ContractId contractId, String listingId,
            String buyerId, String sellerId,
            String productName, String buyerOrgName, String sellerOrgName,
            String buyerEmail, String sellerEmail,
            ContractTerms terms, ContractStatus status,
            String cancelReason, CancelledBy cancelledBy,
            int termsRevision,
            Set<String> signatories) {
        Contract contract = new Contract();

        contract.contractId = contractId;
        contract.listingId = listingId;
        contract.productName = productName;
        contract.buyerId = buyerId;
        contract.buyerOrgName = buyerOrgName;
        contract.buyerEmail = buyerEmail;
        contract.sellerId = sellerId;
        contract.sellerOrgName = sellerOrgName;
        contract.sellerEmail = sellerEmail;
        contract.terms = terms;
        contract.termsRevision = termsRevision;
        contract.status = status;
        contract.cancelReason = cancelReason;
        contract.cancelledBy = cancelledBy;
        contract.signatories = new HashSet<>(signatories);

        return contract;
    }

    public static Contract offer(ContractId contractId, String listingId,
                                 String buyerId, String sellerId,
                                 String productName, String buyerOrgName, String sellerOrgName,
                                 String buyerEmail, String sellerEmail,
                                 ContractTerms terms) {
        Contract contract = new Contract();
        contract.contractId = contractId;
        contract.listingId = listingId;
        contract.buyerId = buyerId;
        contract.sellerId = sellerId;
        contract.buyerOrgName = buyerOrgName;
        contract.sellerOrgName = sellerOrgName;
        contract.buyerEmail = buyerEmail;
        contract.sellerEmail = sellerEmail;
        contract.terms = terms;
        contract.termsRevision = 1;
        contract.status = ContractStatus.OFFERED;
        contract.productName = productName;

        contract.domainEvents.add(new ContractOfferedEvent(contractId.value(), buyerEmail, sellerEmail, buyerId, sellerId, listingId, terms));

        return contract;
    }

    public void counterOffer(String userId, ContractTerms newTerms) {
        //Guard
        if (!userId.equals(this.sellerId) && !userId.equals(this.buyerId)) {
            throw new UnauthorizedContractAccessException("This user doesn't have right to access.");
        }

        if (this.status != ContractStatus.OFFERED && this.status != ContractStatus.NEGOTIATING) {
            throw new IllegalArgumentException("This contract can't be update.");
        }

        //Mutate
        this.status = ContractStatus.NEGOTIATING;
        this.terms = newTerms;
        this.termsRevision++;
        this.signatories.clear();

        //Emit
        this.domainEvents.add(new ContractNegotiatingEvent(
                this.contractId.value(), this.buyerEmail, this.sellerEmail,
                userId, this.termsRevision, newTerms));
    }

    public void sign(String userId) {
        //Guard
        if (!userId.equals(sellerId) && !userId.equals(buyerId)) {
            throw new UnauthorizedContractAccessException("This user doesn't have right to access.");
        }

        if (this.status != ContractStatus.NEGOTIATING && this.status != ContractStatus.OFFERED) {
            throw new IllegalArgumentException("This contract can't be update.");
        }

        if (signatories.contains(userId)) {
            throw new IllegalArgumentException("This user already signed.");
        }
        //Mutate
        this.signatories.add(userId);
        if (this.signatories.contains(sellerId) && this.signatories.contains(buyerId)) {
            this.status = ContractStatus.SIGNED;
        }
        //Emit
        if (this.status == ContractStatus.SIGNED) {
            this.domainEvents.add(new ContractSignedEvent(this.contractId.value(), this.buyerEmail, this.sellerEmail, this.buyerId, this.sellerId, this.listingId, this.terms));
        } else {
            Set<String> remaining = new HashSet<>(Set.of(buyerId, sellerId));
            remaining.removeAll(signatories);
            this.domainEvents.add(new ContractPartiallySignedEvent(this.contractId.value(), this.buyerEmail, this.sellerEmail, userId, remaining));
        }
    }

    /**
     * Called when escrow.locked event received
     */
    public void activate() {
        //Guard
        if (this.status != ContractStatus.SIGNED) {
            throw new IllegalArgumentException("This contract can't be activated.");
        }
        //Mutate
        this.status = ContractStatus.ACTIVE;
        //Emit
        this.domainEvents.add(new ContractActivatedEvent(this.contractId.value(), this.buyerEmail, this.sellerEmail));
    }

    /**
     * Only BUYER can call, only from ACTIVE
     */
    public void confirmDelivery(String buyerId) {
        //Guard
        if (!buyerId.equals(this.buyerId)) {
            throw new UnauthorizedContractAccessException("This user doesn't have right to access.");
        }
        if (this.status != ContractStatus.ACTIVE) {
            throw new IllegalArgumentException("This contract can't be confirmed.");
        }
        //Mutate
        this.status = ContractStatus.DELIVERED;
        //Emit
        this.domainEvents.add(new ContractDeliveredEvent(this.contractId.value(), this.buyerEmail, this.sellerEmail, buyerId));
    }

    /**
     * Called when escrow.released event received
     */
    public void settle() {
        //Guard
        if (this.status != ContractStatus.DELIVERED) {
            throw new IllegalArgumentException("This contract can't be settled.");
        }
        //Mutate
        this.status = ContractStatus.SETTLED;
        //Emit
        this.domainEvents.add(new ContractSettledEvent(this.contractId.value(), this.buyerEmail, this.sellerEmail, this.buyerId, this.sellerId));
    }

    /**
     * Only from ACTIVE, both parties can cancel
     */
    public void cancel(String userId, String reason) {
        //Guard
        if (!userId.equals(this.sellerId) && !userId.equals(this.buyerId)) {
            throw new UnauthorizedContractAccessException("This user doesn't have right to access.");
        }

        if (this.status != ContractStatus.ACTIVE) {
            throw new IllegalArgumentException("This contract can't be cancelled.");
        }
        //Mutate
        this.status = ContractStatus.CANCELLED;
        if (userId.equals(this.buyerId)) {
            this.cancelledBy = CancelledBy.BUYER;
        } else {
            this.cancelledBy = CancelledBy.SELLER;
        }
        this.cancelReason = reason;
        //Emit
        this.domainEvents.add(new ContractCancelledEvent(this.contractId.value(), buyerEmail, sellerEmail, this.cancelledBy, terms.buyerPenaltyRate(), reason));
    }

    /**
     * Only BUYER, only from DELIVERED
     */
    public void dispute(String buyerId, String reason) {
        //Guard
        if (!buyerId.equals(this.buyerId)) {
            throw new UnauthorizedContractAccessException("This user doesn't have right to access.");
        }

        if (this.status != ContractStatus.DELIVERED) {
            throw new IllegalArgumentException("This contract can't be disputed.");
        }
        //Mutate
        this.status = ContractStatus.DISPUTED;
        //Emit
        this.domainEvents.add(new ContractDisputedEvent(contractId.value(), buyerEmail, sellerEmail, buyerId, reason));
    }

    /**
     * Called when escrow.arbitrated confirms that the disputed funds were allocated.
     */
    public void resolveDispute() {
        if (this.status != ContractStatus.DISPUTED) {
            throw new IllegalArgumentException("This contract dispute can't be resolved.");
        }

        this.status = ContractStatus.SETTLED;
        this.domainEvents.add(new ContractSettledEvent(
                this.contractId.value(), this.buyerEmail, this.sellerEmail,
                this.buyerId, this.sellerId));
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }

    public Set<String> getSignatories() {
        return Set.copyOf(signatories);
    }
}
