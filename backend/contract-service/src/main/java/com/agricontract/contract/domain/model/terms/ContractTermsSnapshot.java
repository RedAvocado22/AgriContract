package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Full terms the parties sign: negotiated part + fixed template profile + frozen goods + derived total.
 * This whole object, flattened to the ContractTermsSnapshot wire shape, is what signedContentHash covers.
 */
public record ContractTermsSnapshot(
        ContractTermsInput negotiated,
        ContractTemplateProfile template,
        CoffeeGoodsTerms goodsTerms,
        VndAmount totalContractValue) {

    /** Builds new terms for create/PATCH and applies every negotiation rule. Stored snapshots are restored without this. */
    public static ContractTermsSnapshot assemble(ContractTermsInput negotiated, CoffeeGoodsTerms goodsTerms,
                                                 ContractTemplateProfile template, ContractTermsRules rules) {
        rules.validateNegotiated(negotiated);
        rules.validateGoods(goodsTerms);
        rules.validateTemplate(template);
        VndAmount total = new VndAmount(negotiated.agreedPrice().value()
                .multiply(sum(negotiated.milestoneSchedule()))
                .setScale(2, RoundingMode.HALF_UP));
        rules.validateTotal(total);
        return new ContractTermsSnapshot(negotiated, template, goodsTerms, total);
    }

    /** Requested quantity in kg is derived only from the milestone schedule. */
    public BigDecimal totalQuantity() {
        return sum(negotiated.milestoneSchedule());
    }

    private static BigDecimal sum(List<MilestoneTerm> schedule) {
        return schedule.stream().map(MilestoneTerm::committedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
