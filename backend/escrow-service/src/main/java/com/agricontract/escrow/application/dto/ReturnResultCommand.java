package com.agricontract.escrow.application.dto;

import java.math.BigDecimal;

/**
 * bank.refund_completed / _failed or bank.release_completed / _failed for an activation deposit return (#247),
 * parsed. sourceEventId is the leg's returnRequestEventId. ledgerEntryId only when returned, failureReason only when
 * refused.
 */
public record ReturnResultCommand(String eventId, String correlationId, String contractId, String sourceEventId,
                                  String userId, String entryType, BigDecimal amount, boolean returned,
                                  String ledgerEntryId, String failureReason) {
}
