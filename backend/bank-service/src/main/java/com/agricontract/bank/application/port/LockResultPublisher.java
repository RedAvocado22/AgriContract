package com.agricontract.bank.application.port;

import com.agricontract.bank.application.dto.LockFundsCommand;
import com.agricontract.bank.domain.model.LedgerEntry;

// Writes bank.lock_completed / bank.lock_failed to the outbox. Must run inside the caller's transaction, so a
// result is never sent for a ledger write that rolled back.
public interface LockResultPublisher {

    void completed(LockFundsCommand command, LedgerEntry entry);

    void failed(LockFundsCommand command, String reason);
}
