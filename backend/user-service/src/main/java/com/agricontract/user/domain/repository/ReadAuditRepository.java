package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.ReadAuditEntry;

public interface ReadAuditRepository {
    // Append-only and durable on return; throws ReadAuditUnavailableException if the entry was not stored,
    // so the caller can refuse to hand out the data.
    void append(ReadAuditEntry entry);
}
