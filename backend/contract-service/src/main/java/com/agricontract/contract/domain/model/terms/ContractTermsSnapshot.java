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

    /** buyerDepositRate × total, rounded to the whole đồng (D38). Fixed at signing and carried by contract.signed. */
    public VndAmount buyerDepositAmount() {
        return deposit(negotiated.buyerDepositRate());
    }

    /** sellerDepositRate × total, rounded the same way; 0.00 when no seller deposit was agreed. */
    public VndAmount sellerDepositAmount() {
        return deposit(negotiated.sellerDepositRate());
    }

    /** HALF_UP to 0 decimals, then written with the 2 decimals the money format requires ("11875617.00"). */
    private VndAmount deposit(BigDecimal rate) {
        return new VndAmount(totalContractValue.value().multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.UNNECESSARY));
    }

    private static BigDecimal sum(List<MilestoneTerm> schedule) {
        return schedule.stream().map(MilestoneTerm::committedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
