package com.agricontract.user.domain.model;

import com.agricontract.user.domain.model.vo.AuditResourceType;

import java.time.Instant;

// One staff read of Restricted data (data-governance §8). caseContext stays null until KYC has a case id.
public record ReadAuditEntry(
        String actorId,
        String actorRole,
        AuditResourceType resourceType,
        String resourceId,
        String caseContext,
        Instant accessedAt
) {
    public static ReadAuditEntry kycProfileRead(String actorId, String actorRole, String userId, Instant accessedAt) {
        return new ReadAuditEntry(actorId, actorRole, AuditResourceType.KYC_PROFILE, userId, null, accessedAt);
    }
}
