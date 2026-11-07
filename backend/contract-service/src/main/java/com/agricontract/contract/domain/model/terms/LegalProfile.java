package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;

/** maxContractualPenaltyRate is a derived snapshot of the governing law's cap, kept in the terms so the signed value is visible. */
public record LegalProfile(
        GoverningLaw governingLaw,
        String contractType,
        BigDecimal maxContractualPenaltyRate,
        DamagesPolicy damagesPolicy) {
}
