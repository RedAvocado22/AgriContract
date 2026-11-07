package com.agricontract.contract.domain.model.terms;

import java.util.UUID;

public record PlotRefSnapshot(
        UUID registryEntryId,
        String householdLabel,
        GeometryType geometryType,
        String geometryHash,
        VerificationLevel verificationLevel) {
}
