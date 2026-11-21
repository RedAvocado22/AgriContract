package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full terms the parties sign: negotiated part + fixed template profile + frozen goods + derived total.
 * This whole object, flattened to the ContractTermsSnapshot wire shape, is what signedContentHash covers.
 * totalContractValue is rounded once to the whole đồng (D65).
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
        ContractTermsInput resolved = negotiated.fundingLeadBusinessDays() != null ? negotiated
                : negotiated.withFundingLeadBusinessDays(ContractTermsRules.DEFAULT_FUNDING_LEAD_DAYS);
        VndAmount total = VndAmount.ofDong(resolved.agreedPrice().value().multiply(sum(resolved.milestoneSchedule())));
        rules.validateTotal(total);
        return new ContractTermsSnapshot(resolved, template, goodsTerms, total);
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

    /** Rounded to the whole đồng, HALF_UP (D38, D65). */
    private VndAmount deposit(BigDecimal rate) {
        return VndAmount.ofDong(totalContractValue.value().multiply(rate));
    }

    /**
     * D64: the lead the parties agreed. Snapshots signed before the term existed carry null and get the default,
     * the same value create/PATCH would have stored.
     */
    public int fundingLeadBusinessDays() {
        Integer days = negotiated.fundingLeadBusinessDays();
        return days != null ? days : ContractTermsRules.DEFAULT_FUNDING_LEAD_DAYS;
    }

    private static BigDecimal sum(List<MilestoneTerm> schedule) {
        return schedule.stream().map(MilestoneTerm::committedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
