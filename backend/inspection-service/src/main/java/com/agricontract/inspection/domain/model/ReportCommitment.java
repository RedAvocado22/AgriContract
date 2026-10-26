package com.agricontract.inspection.domain.model;

import static com.agricontract.inspection.domain.model.HashMaterialValidation.*;

/** Exactly the design's hash projection. Workflow supplies authoritative identity/evidence. */
public record ReportCommitment(String reportId, String contractId, String milestoneId, String tier,
        String reportFileHash, String resultHash, String sourceTimestamp, String actorOrSourceIdentity) {
    public ReportCommitment {
        uuid(reportId); uuid(contractId); uuid(milestoneId);
        require("LEVEL_1_5".equals(tier) || "LEVEL_2".equals(tier), "Unsupported inspection tier");
        hash(reportFileHash); hash(resultHash); timestamp(sourceTimestamp);
        require(actorOrSourceIdentity != null && !actorOrSourceIdentity.isBlank(), "Actor/source identity required");
        // Level 2 identity mapping intentionally belongs to its future integration contract.
    }
}
