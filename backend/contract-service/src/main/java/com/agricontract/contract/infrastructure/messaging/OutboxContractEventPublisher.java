package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * contract.signed (golden-flow-events.yaml contractSigned, D38) into event_outbox, in the verify transaction. The
 * java-eventing poller sends it to agricontract.contracts and resends with the same eventId until confirmed.
 * Consumers (escrow, product, analytics, audit) refuse a payload outside the schema, so it is checked here: a
 * contract that cannot produce a valid event cannot become SIGNED (the verify rolls back) instead of failing later
 * in a DLQ.
 */
@Component
@RequiredArgsConstructor
public class OutboxContractEventPublisher implements ContractEventPublisher {
    static final String PRODUCER = "contract-service";
    static final String CONTRACT_SIGNED = "contract.signed";

    private static final Pattern UUID_TEXT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final OutboxWriter outbox;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void contractSigned(Contract contract, Instant signedAt) {
        if (contract.getStatus() != ContractStatus.SIGNED || contract.getCoffeeTerms() == null) {
            throw new IllegalStateException("contract.signed for contract " + contract.getContractId().value()
                    + " in status " + contract.getStatus());
        }
        String contractId = uuid(contract.getContractId().value(), "contractId");
        ContractTermsSnapshot terms = contract.getCoffeeTerms().snapshot();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("listingId", uuid(contract.getListingId(), "listingId"));
        // Not stored apart from the terms yet: equal to Σ committedQuantity by F18 at PATCH (#93). #232 stores the
        // quantity product-service reserved and this reads it (D37).
        payload.put("reservedQuantity", terms.totalQuantity());
        payload.put("commodity", "COFFEE");
        payload.put("buyerId", uuid(contract.getBuyerId(), "buyerId"));
        payload.put("sellerId", uuid(contract.getSellerId(), "sellerId"));
        payload.put("totalAmount", terms.totalContractValue().wire());
        payload.put("buyerDepositAmount", terms.buyerDepositAmount().wire());
        payload.put("sellerDepositAmount", terms.sellerDepositAmount().wire());
        payload.put("signedContentHash", contract.getCoffeeTerms().signedContentHash());
        payload.put("signedAt", signedAt.toString());

        // correlationId = contractId: escrow carries it into the deposit lock chain, so every event of one
        // contract can be joined in the logs. Root event: no causationId.
        outbox.append(new EventEnvelope(UUID.randomUUID(), CONTRACT_SIGNED, 1, signedAt, PRODUCER, contractId,
                contractId, null, null, payload));
    }

    private static String uuid(String value, String field) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw new IllegalStateException("contract.signed needs " + field + " as a UUID, got " + value);
        }
        return value;
    }
}
