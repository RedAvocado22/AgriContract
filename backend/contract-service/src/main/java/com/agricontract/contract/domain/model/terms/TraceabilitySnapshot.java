package com.agricontract.contract.domain.model.terms;

import java.util.UUID;

/** Reference plus fingerprint of a frozen EUDR evidence package; the files themselves stay outside the terms. */
public record TraceabilitySnapshot(UUID evidencePackageId, String packageHash) {
}
