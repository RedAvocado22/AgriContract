package com.agricontract.user.domain.repository;

import com.agricontract.user.domain.model.RoleChangeAuditEntry;

public interface RoleChangeAuditRepository {
    // Append-only; joins the caller's transaction, so a role change is never committed without its entry.
    void append(RoleChangeAuditEntry entry);
}
