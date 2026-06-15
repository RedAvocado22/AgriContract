package com.agricontract.contract.domain.model;

import com.agricontract.contract.domain.event.ContractNegotiatingEvent;
import com.agricontract.contract.domain.event.ContractOfferedEvent;
import com.agricontract.contract.domain.event.DomainEvent;
import com.agricontract.contract.domain.model.vo.CancelledBy;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.model.vo.ContractTerms;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private String buyerEmail;        // snapshot at offer time
    private String sellerEmail;       // snapshot at offer time
    private ContractTerms terms;
    private ContractStatus status;
    private String cancelReason;
    private CancelledBy cancelledBy;       // "BUYER" or "SELLER"
    private Set<String> signatories;
    private List<DomainEvent> domainEvents;

    private Contract() {
        this.signatories = new HashSet<>();
        this.domainEvents = new ArrayList<>();
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
        contract.status = ContractStatus.OFFERED;
        contract.productName = productName;

        contract.domainEvents.add(new ContractOfferedEvent(contractId.value(), buyerEmail, sellerEmail, buyerId, sellerId, listingId, terms));

        return contract;
    }

    public void counterOffer(String userId, ContractTerms newTerms) {
        //Guard
        if (!userId.equals(sellerId) && !userId.equals(buyerId)) {
            throw new IllegalArgumentException("This user doesn't have right to access.");
        }

        if (this.status != ContractStatus.OFFERED && this.status != ContractStatus.NEGOTIATING) {
            throw new IllegalArgumentException("This contract can't be update.");
        }

        //Mutate
        this.status = ContractStatus.NEGOTIATING;
        this.terms = newTerms;

        //Emit
        this.domainEvents.add(new ContractNegotiatingEvent(this.contractId.value(), buyerEmail, sellerEmail, userId, newTerms));
    }

    public void sign(String userId) {

        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Called when escrow.locked event received
     */
    public void activate() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Only BUYER can call, only from ACTIVE
     */
    public void confirmDelivery(String buyerId) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Called when escrow.released event received
     */
    public void settle() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Only from ACTIVE, both parties can cancel
     */
    public void cancel(String userId, String reason) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Only BUYER, only from DELIVERED
     */
    public void dispute(String buyerId, String reason) {
        throw new UnsupportedOperationException("TODO");
    }
}
