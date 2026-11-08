package com.agricontract.bank.application.dto;

import com.agricontract.bank.domain.model.EntryType;
import com.agricontract.bank.domain.model.FundType;

import java.math.BigDecimal;

// One bank command (bank.lock_requested, bank.release_requested, bank.refund_requested), already parsed. requestEventId == sourceEventId (checked by the consumer); both are kept
// because the result needs the request eventId as causationId and the payload's sourceEventId echoed back.
public record LedgerCommand(
        String requestEventId,
        String correlationId,
        String contractId,
        String milestoneId,
        String userId,
        EntryType entryType,
        FundType fundType,
        String remedyDecisionId,
        String remedyLegId,
        BigDecimal amount,
        String sourceEventId
) {

    /** Which command this is: its entry type decides (LedgerOperation). */
    public LedgerOperation operation() {
        return LedgerOperation.of(entryType);
    }
}
