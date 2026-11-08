package com.agricontract.contract.infrastructure.messaging;

import com.agricontract.contract.application.port.ContractEventPublisher;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.activation.ActivationRecovery;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
    static final String CONTRACT_ANCHOR_REQUESTED = "notification.contract_anchor_requested";
    static final String CONTRACT_ACTIVATED = "contract.activated";
    static final String ACTIVATION_FAILED_REQUESTED = "notification.contract_activation_failed_requested";
    static final String DEPOSIT_LOCK_RETRY_REQUESTED = "contract.deposit_lock_retry_requested";
    static final String ACTIVATION_REFUND_REQUESTED = "contract.activation_refund_requested";
    static final String ACTIVATION_CANCEL_REQUESTED = "notification.activation_cancel_requested";
    static final String ACTIVATION_FAILED = "contract.activation_failed";

    private static final Pattern UUID_TEXT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

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

    /**
     * notification-commands.yaml contractAnchorRequested. Recipients are the buyer and seller emails stored on the
     * contract at offer time (D38); the snapshot is the exact canonical JSON the parties signed, as an object.
     * Routed by its eventType on agricontract.contracts, like every command (notification design §2.1).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void contractAnchorRequested(Contract contract, Instant signedAt) {
        if (contract.getStatus() != ContractStatus.SIGNED || contract.getCoffeeTerms() == null) {
            throw new IllegalStateException("contract_anchor_requested for contract "
                    + contract.getContractId().value() + " in status " + contract.getStatus());
        }
        String contractId = uuid(contract.getContractId().value(), "contractId");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("recipients", List.of(
                recipient(contract.getBuyerId(), contract.getBuyerEmail(), "BUYER"),
                recipient(contract.getSellerId(), contract.getSellerEmail(), "SELLER")));
        payload.put("contractTermsSnapshot", snapshot(contract));
        payload.put("signedContentHash", contract.getCoffeeTerms().signedContentHash());
        payload.put("signedAt", signedAt.toString());

        outbox.append(new EventEnvelope(UUID.randomUUID(), CONTRACT_ANCHOR_REQUESTED, 1, signedAt, PRODUCER,
                contractId, contractId, null, null, payload));
    }

    /**
     * contract.activated (golden-flow-events.yaml contractActivated): product-service moves the reservation from
     * SIGNED_RELEASABLE to COMMITTED. reservedQuantity is the terms' total until #232 (as for contract.signed).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void contractActivated(Contract contract, Instant activatedAt, String correlationId, String causationId) {
        if (contract.getStatus() != ContractStatus.ACTIVE || contract.getCoffeeTerms() == null) {
            throw new IllegalStateException("contract.activated for contract " + contract.getContractId().value()
                    + " in status " + contract.getStatus());
        }
        String contractId = uuid(contract.getContractId().value(), "contractId");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("listingId", uuid(contract.getListingId(), "listingId"));
        payload.put("reservedQuantity", contract.getCoffeeTerms().snapshot().totalQuantity());
        payload.put("activatedAt", activatedAt.toString());

        outbox.append(new EventEnvelope(UUID.randomUUID(), CONTRACT_ACTIVATED, 1, activatedAt, PRODUCER, contractId,
                correlationId, causationId, null, payload));
    }

    /**
     * notification-commands.yaml contractActivationFailedRequested. Recipients are the parties' emails stored on the
     * contract; OPERATOR is reached by notification-service's own operator address (notification design §8).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void contractActivationFailedRequested(Contract contract, ActivationRecovery recovery,
                                                  String buyerDepositState, String sellerDepositState,
                                                  String correlationId, String causationId) {
        String contractId = uuid(contract.getContractId().value(), "contractId");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("recipients", List.of(
                recipient(contract.getBuyerId(), contract.getBuyerEmail(), "BUYER"),
                recipient(contract.getSellerId(), contract.getSellerEmail(), "SELLER")));
        payload.put("failedLeg", recovery.lastFailedLeg().name());
        payload.put("buyerDepositState", buyerDepositState);
        payload.put("sellerDepositState", sellerDepositState);
        payload.put("nextActions", List.of("RETRY_DEPOSIT_LOCK", "REQUEST_ACTIVATION_CANCEL"));
        payload.put("oneSidedCancelAllowedFrom", recovery.oneSidedCancelAllowedFrom().toString());

        outbox.append(new EventEnvelope(UUID.randomUUID(), ACTIVATION_FAILED_REQUESTED, 1, recovery.lastFailedAt(),
                PRODUCER, contractId, correlationId, causationId, null, payload));
    }

    /** golden-flow-events.yaml contractDepositLockRetryRequested, to escrow-service. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void depositLockRetryRequested(Contract contract, String operatorUserId, Instant requestedAt) {
        String contractId = uuid(contract.getContractId().value(), "contractId");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("operatorUserId", uuid(operatorUserId, "operatorUserId"));
        payload.put("requestedAt", requestedAt.toString());

        outbox.append(new EventEnvelope(UUID.randomUUID(), DEPOSIT_LOCK_RETRY_REQUESTED, 1, requestedAt, PRODUCER,
                contractId, contractId, null, null, payload));
    }

    /** golden-flow-events.yaml contractActivationRefundRequested, to escrow-service. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void activationRefundRequested(Contract contract, List<SignerRole> requestedBy, String trigger,
                                          Instant at) {
        String contractId = uuid(contract.getContractId().value(), "contractId");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("requestedBy", requestedBy.stream().map(Enum::name).toList());
        payload.put("trigger", trigger);
        payload.put("requestedAt", at.toString());
        outbox.append(new EventEnvelope(UUID.randomUUID(), ACTIVATION_REFUND_REQUESTED, 1, at, PRODUCER, contractId,
                contractId, null, null, payload));
    }

    /** notification-commands.yaml activationCancelRequested: to the other party (OPERATOR via its own address). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void activationCancelRequested(Contract contract, SignerRole requestedBy, Instant oneSidedCancelAllowedFrom,
                                          Instant at) {
        String contractId = uuid(contract.getContractId().value(), "contractId");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("recipients", List.of(requestedBy == SignerRole.BUYER
                ? recipient(contract.getSellerId(), contract.getSellerEmail(), "SELLER")
                : recipient(contract.getBuyerId(), contract.getBuyerEmail(), "BUYER")));
        payload.put("requestedByRole", requestedBy.name());
        payload.put("oneSidedCancelAllowedFrom", oneSidedCancelAllowedFrom.toString());
        outbox.append(new EventEnvelope(UUID.randomUUID(), ACTIVATION_CANCEL_REQUESTED, 1, at, PRODUCER, contractId,
                contractId, null, null, payload));
    }

    /** golden-flow-events.yaml contractActivationFailed, after every deposit came back (lock = 0). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void contractActivationFailed(Contract contract, Instant failedAt, String correlationId,
                                         String causationId) {
        if (contract.getStatus() != ContractStatus.ACTIVATION_FAILED || contract.getCoffeeTerms() == null) {
            throw new IllegalStateException("contract.activation_failed for contract "
                    + contract.getContractId().value() + " in status " + contract.getStatus());
        }
        String contractId = uuid(contract.getContractId().value(), "contractId");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", contractId);
        payload.put("listingId", uuid(contract.getListingId(), "listingId"));
        payload.put("reservedQuantity", contract.getCoffeeTerms().snapshot().totalQuantity());
        payload.put("activationFailedAt", failedAt.toString());
        outbox.append(new EventEnvelope(UUID.randomUUID(), ACTIVATION_FAILED, 1, failedAt, PRODUCER, contractId,
                correlationId, causationId, null, payload));
    }

    private static Map<String, Object> recipient(String userId, String email, String role) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("contract_anchor_requested needs the " + role + " email");
        }
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("userId", uuid(userId, role.toLowerCase() + "Id"));
        recipient.put("email", email);
        recipient.put("role", role);
        return recipient;
    }

    private Map<String, Object> snapshot(Contract contract) {
        try {
            // BigDecimal, not double: the numbers must reach the email exactly as signed.
            return objectMapper.readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(contract.getCoffeeTerms().canonicalJson());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored terms of " + contract.getContractId().value()
                    + " are not a JSON object", e);
        }
    }

    private static String uuid(String value, String field) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw new IllegalStateException("contract.signed needs " + field + " as a UUID, got " + value);
        }
        return value;
    }
}
