package com.agricontract.contract.domain.model.terms;

/** Which fixed legal template and coffee-quality profile the parties sign together with the negotiated terms. */
public record ContractTemplateProfile(
        String contractTemplateCode,
        String contractTemplateVersion,
        String contractTemplateHash,
        String coffeeQualityProfileVersion) {
}
