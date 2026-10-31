package com.agricontract.inspection.domain.service;

import com.agricontract.inspection.domain.model.InspectionSettlementResultV1;
import com.agricontract.inspection.domain.model.ReportCommitment;

public interface InspectionCanonicalizer {
    byte[] canonicalize(InspectionSettlementResultV1 result);

    byte[] canonicalize(ReportCommitment report);
}
