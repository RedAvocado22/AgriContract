package com.agricontract.inspection.domain.model;

import static com.agricontract.inspection.domain.model.HashMaterialValidation.hash;
import static com.agricontract.inspection.domain.model.HashMaterialValidation.require;
import static com.agricontract.inspection.domain.model.HashMaterialValidation.timestamp;
import static com.agricontract.inspection.domain.model.HashMaterialValidation.uuid;

/** Exactly the design's hash projection. Workflow supplies authoritative identity/evidence. */
public record ReportCommitment(
        String reportId,
        String contractId,
        String milestoneId,
        InspectionTier tier,
        String reportFileHash,
        String resultHash,
        String sourceTimestamp,
        String actorOrSourceIdentity) {
    public ReportCommitment {
        uuid(reportId);
        uuid(contractId);
        uuid(milestoneId);
        require(tier != null, "Inspection tier required");
        hash(reportFileHash);
        hash(resultHash);
        timestamp(sourceTimestamp);
        require(
                actorOrSourceIdentity != null && !actorOrSourceIdentity.isBlank(),
                "Actor/source identity required");
        if (tier == InspectionTier.LEVEL_1_5) {
            uuid(actorOrSourceIdentity);
        }
        // Level 2 identity mapping intentionally belongs to its future integration contract.
    }
}
