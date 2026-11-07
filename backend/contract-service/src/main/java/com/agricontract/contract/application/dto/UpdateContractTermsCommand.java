package com.agricontract.contract.application.dto;

import com.agricontract.contract.domain.model.terms.ContractTermsInput;

import java.util.UUID;

public record UpdateContractTermsCommand(String contractId, String actorId, UUID idempotencyKey,
                                         ContractTermsInput terms) {
}
