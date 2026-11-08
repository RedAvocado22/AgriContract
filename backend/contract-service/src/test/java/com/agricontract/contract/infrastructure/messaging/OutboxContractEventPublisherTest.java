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
    private final OutboxContractEventPublisher publisher = new OutboxContractEventPublisher(outbox);
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

    private EventEnvelope appended() {
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        return captor.getValue();
    }

    private Contract contract(ContractStatus status, String contractId, String buyerId) {
        CanonicalTerms terms = CanonicalTerms.of(snapshot(), canonicalizer);
        String hash = terms.signedContentHash();
        return Contract.reconstitute(new ContractId(contractId), goods().listingId().toString(), buyerId, SELLER,
                "Cà phê", "HTX Mua", "HTX Bán", "b@x.test", "s@x.test", null, terms, status,
                Map.of(SignerRole.BUYER, hash, SignerRole.SELLER, hash), 0L);
    }
}
