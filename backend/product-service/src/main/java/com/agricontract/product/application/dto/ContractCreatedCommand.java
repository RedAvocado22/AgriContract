package com.agricontract.product.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

// contract.created payload plus the envelope eventId used for dedup
public record ContractCreatedCommand(
        UUID eventId,
        String contractId,
        String listingId,
        BigDecimal reservedQuantity
) {}
