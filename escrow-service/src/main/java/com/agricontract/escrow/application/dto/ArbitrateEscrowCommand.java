package com.agricontract.escrow.application.dto;

import java.math.BigDecimal;

public record ArbitrateEscrowCommand(String contractId, BigDecimal buyerAmount, BigDecimal sellerAmount,
                                     String justification) {
}
