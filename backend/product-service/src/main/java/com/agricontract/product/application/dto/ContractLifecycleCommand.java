package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;

import java.math.BigDecimal;
import java.util.UUID;

// contract.signed / activated / activation_failed / withdrawn: the fields inventory needs, plus the envelope eventId
public record ContractLifecycleCommand(
        UUID eventId,
        ContractLifecycleEventType eventType,
        String contractId,
        String listingId,
        BigDecimal reservedQuantity
) {}
