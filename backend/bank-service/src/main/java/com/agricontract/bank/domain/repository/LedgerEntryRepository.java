package com.agricontract.bank.domain.repository;

import com.agricontract.bank.domain.model.LedgerEntry;

// Append-only: no update or delete method, on purpose.
public interface LedgerEntryRepository {

    void append(LedgerEntry entry);
}
