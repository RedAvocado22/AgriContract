package com.agricontract.contract.domain.model;

import com.agricontract.contract.domain.event.*;
import com.agricontract.contract.domain.exception.UnauthorizedContractAccessException;
import com.agricontract.contract.domain.model.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ContractTest {

    private static final String BUYER_ID = "buyer-1";
    private static final String SELLER_ID = "seller-1";
    private static final String BUYER_EMAIL = "buyer@test.com";
    private static final String SELLER_EMAIL = "seller@test.com";
    private static final String LISTING_ID = "listing-1";
    private static final ContractId CONTRACT_ID = new ContractId("contract-1");

    private ContractTerms terms;

    @BeforeEach
    void setUp() {
        terms = new ContractTerms(
                new Quantity(new BigDecimal("100"), "kg"),
                new Money(new BigDecimal("500000"), "VND"),
                LocalDate.now().plusDays(30),
                new BigDecimal("0.30"),
                new BigDecimal("0.10"),
                "Grade A"
        );
    }

    private Contract offered() {
        return Contract.offer(CONTRACT_ID, LISTING_ID, BUYER_ID, SELLER_ID,
                "Rice", "Buyer Org", "Seller Org", BUYER_EMAIL, SELLER_EMAIL, terms);
    }

    // ─── offer ───────────────────────────────────────────────────────────────

    @Test
    void offer_happyPath_contractCreatedWithOfferedStatusAndEvent() {
        Contract contract = offered();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
        assertThat(contract.getBuyerId()).isEqualTo(BUYER_ID);
        assertThat(contract.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(contract.getTerms()).isEqualTo(terms);

        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractOfferedEvent.class);
    }

    // ─── counterOffer ─────────────────────────────────────────────────────────

    @Test
    void counterOffer_fromOffered_statusNegotiatingAndEventEmitted() {
        Contract contract = offered();
        ContractTerms newTerms = newTerms();

        contract.counterOffer(BUYER_ID, newTerms);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.NEGOTIATING);
        assertThat(contract.getTerms()).isEqualTo(newTerms);

        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(1)).isInstanceOf(ContractNegotiatingEvent.class);
    }

    @Test
    void counterOffer_fromNegotiating_eventEmittedAgain() {
        Contract contract = offered();
        contract.counterOffer(BUYER_ID, newTerms());
        contract.pullDomainEvents();

        ContractTerms secondTerms = newTerms();
        contract.counterOffer(SELLER_ID, secondTerms);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.NEGOTIATING);
        assertThat(contract.getTerms()).isEqualTo(secondTerms);

        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractNegotiatingEvent.class);
    }

    @Test
    void counterOffer_invalidStatus_throws() {
        Contract contract = signedContract();

        assertThatThrownBy(() -> contract.counterOffer(BUYER_ID, newTerms()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void counterOffer_invalidUser_throws() {
        Contract contract = offered();

        assertThatThrownBy(() -> contract.counterOffer("stranger-id", newTerms()))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    // ─── sign ─────────────────────────────────────────────────────────────────

    @Test
    void sign_buyerOnly_emitsPartiallySignedEvent() {
        Contract contract = offered();
        contract.pullDomainEvents();

        contract.sign(BUYER_ID);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractPartiallySignedEvent.class);
    }

    @Test
    void sign_sellerOnly_emitsPartiallySignedEvent() {
        Contract contract = offered();
        contract.pullDomainEvents();

        contract.sign(SELLER_ID);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.OFFERED);
        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractPartiallySignedEvent.class);
    }

    @Test
    void sign_bothParties_statusSignedAndSignedEventEmitted() {
        Contract contract = offered();
        contract.sign(BUYER_ID);
        contract.pullDomainEvents();

        contract.sign(SELLER_ID);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGNED);
        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractSignedEvent.class);
    }

    @Test
    void sign_doubleSign_throws() {
        Contract contract = offered();
        contract.sign(BUYER_ID);

        assertThatThrownBy(() -> contract.sign(BUYER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sign_invalidStatus_throws() {
        Contract contract = signedContract();

        assertThatThrownBy(() -> contract.sign(BUYER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sign_invalidUser_throws() {
        Contract contract = offered();

        assertThatThrownBy(() -> contract.sign("stranger-id"))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    // ─── activate ────────────────────────────────────────────────────────────

    @Test
    void activate_happyPath_statusActiveAndEventEmitted() {
        Contract contract = signedContract();
        contract.pullDomainEvents();

        contract.activate();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractActivatedEvent.class);
    }

    @Test
    void activate_invalidStatus_throws() {
        Contract contract = offered();

        assertThatThrownBy(contract::activate)
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── confirmDelivery ──────────────────────────────────────────────────────

    @Test
    void confirmDelivery_happyPath_statusDeliveredAndEventEmitted() {
        Contract contract = activeContract();
        contract.pullDomainEvents();

        contract.confirmDelivery(BUYER_ID);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.DELIVERED);
        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractDeliveredEvent.class);
    }

    @Test
    void confirmDelivery_invalidStatus_throws() {
        Contract contract = offered();

        assertThatThrownBy(() -> contract.confirmDelivery(BUYER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmDelivery_wrongUser_throws() {
        Contract contract = activeContract();

        assertThatThrownBy(() -> contract.confirmDelivery(SELLER_ID))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    // ─── settle ───────────────────────────────────────────────────────────────

    @Test
    void settle_happyPath_statusSettledAndEventEmitted() {
        Contract contract = deliveredContract();
        contract.pullDomainEvents();

        contract.settle();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SETTLED);
        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractSettledEvent.class);
    }

    @Test
    void settle_invalidStatus_throws() {
        Contract contract = activeContract();

        assertThatThrownBy(contract::settle)
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── cancel ───────────────────────────────────────────────────────────────

    @Test
    void cancel_byBuyer_cancelledByBuyerAndEventEmitted() {
        Contract contract = activeContract();
        contract.pullDomainEvents();

        contract.cancel(BUYER_ID, "Changed mind");

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.getCancelledBy()).isEqualTo(CancelledBy.BUYER);
        assertThat(contract.getCancelReason()).isEqualTo("Changed mind");

        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractCancelledEvent.class);
    }

    @Test
    void cancel_bySeller_cancelledBySellerAndEventEmitted() {
        Contract contract = activeContract();
        contract.pullDomainEvents();

        contract.cancel(SELLER_ID, "Cannot deliver");

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.getCancelledBy()).isEqualTo(CancelledBy.SELLER);
    }

    @Test
    void cancel_invalidStatus_throws() {
        Contract contract = offered();

        assertThatThrownBy(() -> contract.cancel(BUYER_ID, "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_invalidUser_throws() {
        Contract contract = activeContract();

        assertThatThrownBy(() -> contract.cancel("stranger-id", "reason"))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    // ─── dispute ──────────────────────────────────────────────────────────────

    @Test
    void dispute_happyPath_statusDisputedAndEventEmitted() {
        Contract contract = deliveredContract();
        contract.pullDomainEvents();

        contract.dispute(BUYER_ID, "Goods damaged");

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.DISPUTED);
        List<DomainEvent> events = contract.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ContractDisputedEvent.class);
    }

    @Test
    void dispute_invalidStatus_throws() {
        Contract contract = activeContract();

        assertThatThrownBy(() -> contract.dispute(BUYER_ID, "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dispute_invalidUser_throws() {
        Contract contract = deliveredContract();

        assertThatThrownBy(() -> contract.dispute(SELLER_ID, "reason"))
                .isInstanceOf(UnauthorizedContractAccessException.class);
    }

    // ─── pullDomainEvents ─────────────────────────────────────────────────────

    @Test
    void pullDomainEvents_calledTwice_secondCallReturnsEmpty() {
        Contract contract = offered();

        contract.pullDomainEvents();
        List<DomainEvent> second = contract.pullDomainEvents();

        assertThat(second).isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private ContractTerms newTerms() {
        return new ContractTerms(
                new Quantity(new BigDecimal("200"), "kg"),
                new Money(new BigDecimal("450000"), "VND"),
                LocalDate.now().plusDays(45),
                new BigDecimal("0.30"),
                new BigDecimal("0.10"),
                "Grade B"
        );
    }

    private Contract signedContract() {
        Contract contract = offered();
        contract.sign(BUYER_ID);
        contract.sign(SELLER_ID);
        return contract;
    }

    private Contract activeContract() {
        Contract contract = signedContract();
        contract.activate();
        return contract;
    }

    private Contract deliveredContract() {
        Contract contract = activeContract();
        contract.confirmDelivery(BUYER_ID);
        return contract;
    }
}
