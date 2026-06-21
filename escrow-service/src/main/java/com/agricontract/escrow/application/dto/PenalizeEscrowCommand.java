package com.agricontract.escrow.application.dto;

import com.agricontract.escrow.domain.model.vo.Party;

import java.math.BigDecimal;

public record PenalizeEscrowCommand(String contractId, Party cancelledBy, BigDecimal buyerPenaltyRate) {
}
