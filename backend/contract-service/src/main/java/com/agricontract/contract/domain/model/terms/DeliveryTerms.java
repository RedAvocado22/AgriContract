package com.agricontract.contract.domain.model.terms;

/** Signed allocation rules only; runtime addresses and carriers are not part of the signed terms. */
public record DeliveryTerms(
        DeliveryResponsibility deliveryResponsibility,
        RiskTransferPoint riskTransferPoint,
        TitleTransferPoint titleTransferPoint,
        CostBearer transportCostBearer,
        CostBearer unloadingCostBearer) {
}
