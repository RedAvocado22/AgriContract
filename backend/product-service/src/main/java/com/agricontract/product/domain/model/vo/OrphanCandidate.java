package com.agricontract.product.domain.model.vo;

import java.time.LocalDateTime;

/** A reservation the reaper may release, with its position in the scan so a run can page past rows it cannot release. */
public record OrphanCandidate(ContractId contractId, LocalDateTime createdAt) {
}
