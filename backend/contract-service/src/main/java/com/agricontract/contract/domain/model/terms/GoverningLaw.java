package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;
import java.util.Set;

/**
 * The penalty cap and the allowed penalty/damages relationships come from the law, not from negotiation
 * (milestone-escrow design §2.1b).
 */
public enum GoverningLaw {
    /** Commercial Law 2005 art. 301: penalty at most 8% of the breached obligation; art. 307: penalty and proven damages both apply. */
    VN_COMMERCIAL_LAW(new BigDecimal("0.08"),
            Set.of(DamagesPolicy.COMMERCIAL_CUMULATIVE_IF_PROVEN, DamagesPolicy.EXPRESS_PENALTY_ONLY)),
    /** Civil Code art. 418: no percentage cap; penalty only unless the contract expressly makes it cumulative. */
    VN_CIVIL_LAW(BigDecimal.ONE,
            Set.of(DamagesPolicy.CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE, DamagesPolicy.EXPRESS_PENALTY_ONLY));

    private final BigDecimal maxContractualPenaltyRate;
    private final Set<DamagesPolicy> allowedDamagesPolicies;

    GoverningLaw(BigDecimal maxContractualPenaltyRate, Set<DamagesPolicy> allowedDamagesPolicies) {
        this.maxContractualPenaltyRate = maxContractualPenaltyRate;
        this.allowedDamagesPolicies = allowedDamagesPolicies;
    }

    public BigDecimal maxContractualPenaltyRate() {
        return maxContractualPenaltyRate;
    }

    public boolean allows(DamagesPolicy policy) {
        return allowedDamagesPolicies.contains(policy);
    }
}
