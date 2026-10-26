package com.agricontract.bank.domain.repository;

import com.agricontract.bank.domain.model.LedgerEntry;

import java.util.Optional;

// Append-only: no update or delete method, on purpose.
public interface LedgerEntryRepository {

    /** @throws com.agricontract.bank.domain.exception.DuplicateLedgerEntryException if the sourceEventId exists */
    void append(LedgerEntry entry);

    Optional<LedgerEntry> findBySourceEventId(String sourceEventId);
}
