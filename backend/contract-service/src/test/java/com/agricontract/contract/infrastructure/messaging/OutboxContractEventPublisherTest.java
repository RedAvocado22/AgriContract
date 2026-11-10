package com.agricontract.contract.infrastructure.messaging;

import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.goods;
import static com.agricontract.contract.domain.model.terms.CoffeeTermsFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.infrastructure.contract.FrozenSchemas;
import com.agricontract.contract.infrastructure.terms.JcsTermsCanonicalizer;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.erdtman.jcs.JsonCanonicalizer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** contract.signed as it goes to the outbox, checked against the frozen schema in contracts/events. */
class OutboxContractEventPublisherTest {
    private static final String BUYER = "0b7e3b7a-1c1e-4c5e-9f0a-3d2b1a0c9e8f";
    private static final String SELLER = "5a4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a";
    private static final Instant SIGNED_AT = Instant.parse("2026-11-08T10:00:00.123456Z");

    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final OutboxContractEventPublisher publisher = new OutboxContractEventPublisher(outbox,
            new ObjectMapper());
    private final JcsTermsCanonicalizer canonicalizer = new JcsTermsCanonicalizer();

    @Test
    void signedContract_matchesTheFrozenSchema_withDepositsAndHashFromTheTerms() throws Exception {
        Contract contract = contract(ContractStatus.SIGNED, UUID.randomUUID().toString(), BUYER);

        publisher.contractSigned(contract, SIGNED_AT);

        EventEnvelope envelope = appended();
        JsonNode json = new ObjectMapper().readTree(new EventEnvelopeCodec().toJson(envelope));
        FrozenSchemas.assertEvent("contractSigned", json);
        String id = contract.getContractId().value();
        assertThat(envelope.aggregateId()).isEqualTo(id);
        assertThat(envelope.correlationId()).isEqualTo(id);
        assertThat(envelope.causationId()).isNull();
        assertThat(envelope.occurredAt()).isEqualTo(SIGNED_AT);
        Map<String, Object> payload = envelope.payload();
        // 95,000 đ/kg × 2,500.5 kg; fixture deposit rates 5% (buyer) and 3% (seller).
        assertThat(payload).containsEntry("totalAmount", "237547500.00")
                .containsEntry("buyerDepositAmount", "11877375.00")
                .containsEntry("sellerDepositAmount", "7126425.00")
                .containsEntry("buyerId", BUYER)
                .containsEntry("sellerId", SELLER)
                .containsEntry("commodity", "COFFEE")
                .containsEntry("signedContentHash", contract.getCoffeeTerms().signedContentHash())
                .containsEntry("signedAt", "2026-11-08T10:00:00.123456Z");
        assertThat(json.at("/payload/reservedQuantity").decimalValue()).isEqualByComparingTo("2500.5");
    }

    @Test
    void notSignedYet_isRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, UUID.randomUUID().toString(), BUYER);

        assertThatThrownBy(() -> publisher.contractSigned(contract, SIGNED_AT))
                .isInstanceOf(IllegalStateException.class);
        verify(outbox, never()).append(any());
    }

    @Test
    void idThatIsNotAUuid_isRefusedHere_notInAConsumersDlq() {
        Contract contract = contract(ContractStatus.SIGNED, UUID.randomUUID().toString(), "buyer-1");

        assertThatThrownBy(() -> publisher.contractSigned(contract, SIGNED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("buyerId");
        verify(outbox, never()).append(any());
    }

    @Test
    void anchorCommand_matchesTheFrozenSchema_withBothPartiesAndTheExactSignedTerms() throws Exception {
        Contract contract = contract(ContractStatus.SIGNED, UUID.randomUUID().toString(), BUYER);

        publisher.contractAnchorRequested(contract, SIGNED_AT);

        EventEnvelope envelope = appended();
        JsonNode json = new ObjectMapper().readTree(new EventEnvelopeCodec().toJson(envelope));
        FrozenSchemas.assertCommand("contractAnchorRequested", json);
        String id = contract.getContractId().value();
        assertThat(envelope.eventType()).isEqualTo("notification.contract_anchor_requested");
        assertThat(envelope.correlationId()).isEqualTo(id);
        assertThat(json.at("/payload/recipients")).hasSize(2);
        assertThat(json.at("/payload/recipients/0/email").asText()).isEqualTo("buyer@example.com");
        assertThat(json.at("/payload/recipients/0/role").asText()).isEqualTo("BUYER");
        assertThat(json.at("/payload/recipients/0/userId").asText()).isEqualTo(BUYER);
        assertThat(json.at("/payload/recipients/1/email").asText()).isEqualTo("seller@example.com");
        assertThat(json.at("/payload/recipients/1/role").asText()).isEqualTo("SELLER");
        assertThat(json.at("/payload/signedContentHash").asText())
                .isEqualTo(contract.getCoffeeTerms().signedContentHash());
        // The snapshot in the email is the signed one: canonicalising it again gives the same hash.
        String resent = new ObjectMapper().writeValueAsString(json.at("/payload/contractTermsSnapshot"));
        assertThat(CanonicalTerms.sha256(new JsonCanonicalizer(resent).getEncodedString()))
                .isEqualTo(contract.getCoffeeTerms().signedContentHash());
    }

    @Test
    void anchorCommand_notSignedYet_isRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, UUID.randomUUID().toString(), BUYER);

        assertThatThrownBy(() -> publisher.contractAnchorRequested(contract, SIGNED_AT))
                .isInstanceOf(IllegalStateException.class);
        verify(outbox, never()).append(any());
    }

    private static final Instant WITHDRAWN_AT = Instant.parse("2026-11-10T12:00:00.654321Z");

    @Test
    void withdrawn_matchesTheFrozenSchema_withTheReservedQuantity() throws Exception {
        Contract contract = contract(ContractStatus.WITHDRAWN, UUID.randomUUID().toString(), BUYER);

        publisher.contractWithdrawn(contract, WITHDRAWN_AT);

        EventEnvelope envelope = appended();
        JsonNode json = new ObjectMapper().readTree(new EventEnvelopeCodec().toJson(envelope));
        FrozenSchemas.assertEvent("contractWithdrawn", json);
        assertThat(envelope.eventType()).isEqualTo("contract.withdrawn");
        assertThat(envelope.correlationId()).isEqualTo(contract.getContractId().value());
        assertThat(json.at("/payload/listingId").asText()).isEqualTo(contract.getListingId());
        assertThat(json.at("/payload/reservedQuantity").decimalValue()).isEqualByComparingTo("2500.5");
        assertThat(json.at("/payload/withdrawnAt").asText()).isEqualTo("2026-11-10T12:00:00.654321Z");
    }

    @Test
    void terminated_withdrawOffer_matchesTheFrozenSchema_withNoBreachNoRemedy() throws Exception {
        Contract contract = contract(ContractStatus.WITHDRAWN, UUID.randomUUID().toString(), BUYER);

        publisher.offerWithdrawnTerminated(contract, SignerRole.SELLER, WITHDRAWN_AT);

        JsonNode json = new ObjectMapper().readTree(new EventEnvelopeCodec().toJson(appended()));
        FrozenSchemas.assertEvent("contractTerminated", json);
        assertThat(json.at("/eventType").asText()).isEqualTo("contract.terminated");
        assertThat(json.at("/payload/terminationType").asText()).isEqualTo("WITHDRAW_OFFER");
        assertThat(json.at("/payload/requestedBy").asText()).isEqualTo("SELLER");
        for (String field : new String[] {"finalBreachingRole", "breachReasonCode", "remedyDecisionId",
                "breachCaseId", "supersededByContractId", "replacesContractId"}) {
            assertThat(json.at("/payload").has(field)).as(field + " on the wire").isTrue();
            assertThat(json.at("/payload/" + field).isNull()).as(field).isTrue();
        }
        assertThat(json.at("/payload/affectedMilestoneIds")).isEmpty();
    }

    @Test
    void withdrawNotice_goesToTheOtherPartyOnly_andMatchesTheFrozenSchema() throws Exception {
        Contract contract = contract(ContractStatus.WITHDRAWN, UUID.randomUUID().toString(), BUYER);

        publisher.offerWithdrawnNoticeRequested(contract, SignerRole.BUYER, WITHDRAWN_AT);

        JsonNode json = new ObjectMapper().readTree(new EventEnvelopeCodec().toJson(appended()));
        FrozenSchemas.assertCommand("contractTerminatedRequested", json);
        assertThat(json.at("/eventType").asText()).isEqualTo("notification.contract_terminated_requested");
        assertThat(json.at("/payload/recipients")).hasSize(1);
        assertThat(json.at("/payload/recipients/0/role").asText()).isEqualTo("SELLER");
        assertThat(json.at("/payload/recipients/0/email").asText()).isEqualTo("seller@example.com");
        assertThat(json.at("/payload/requestedBy").asText()).isEqualTo("BUYER");
        assertThat(json.at("/payload/terminationType").asText()).isEqualTo("WITHDRAW_OFFER");
        assertThat(json.at("/payload/remedyLegs")).isEmpty();
    }

    @Test
    void withdrawEvents_forAContractNotWithdrawn_areRefused() {
        Contract contract = contract(ContractStatus.NEGOTIATING, UUID.randomUUID().toString(), BUYER);

        assertThatThrownBy(() -> publisher.contractWithdrawn(contract, WITHDRAWN_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> publisher.offerWithdrawnTerminated(contract, SignerRole.BUYER, WITHDRAWN_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> publisher.offerWithdrawnNoticeRequested(contract, SignerRole.BUYER, WITHDRAWN_AT))
                .isInstanceOf(IllegalStateException.class);
        verify(outbox, never()).append(any());
    }

    private EventEnvelope appended() {
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        return captor.getValue();
    }

    private Contract contract(ContractStatus status, String contractId, String buyerId) {
        CanonicalTerms terms = CanonicalTerms.of(snapshot(), canonicalizer);
        String hash = terms.signedContentHash();
        return Contract.reconstitute(new ContractId(contractId), goods().listingId().toString(), buyerId, SELLER,
                "Cà phê", "HTX Mua", "HTX Bán", "buyer@example.com", "seller@example.com", null, terms, status,
                Map.of(SignerRole.BUYER, hash, SignerRole.SELLER, hash), 0L);
    }
}
