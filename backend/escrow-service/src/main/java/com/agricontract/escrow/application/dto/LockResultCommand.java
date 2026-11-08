package com.agricontract.escrow.application.dto;

import java.math.BigDecimal;

/**
 * bank.lock_completed or bank.lock_failed (golden-flow-events.yaml), parsed and checked by the consumer.
 * sourceEventId is the leg's requestEventId. ledgerEntryId is set only when locked, failureReason only when failed.
 */
public record LockResultCommand(
        String eventId,
        String correlationId,
        String contractId,
        String sourceEventId,
        String userId,
        String entryType,
        BigDecimal amount,
        boolean locked,
        String ledgerEntryId,
        String failureReason) {
}
