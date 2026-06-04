package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.TransactionType;
import lombok.Getter;

import java.time.LocalDateTime;

// Entity — append-only financial ledger (không bao giờ update, chỉ insert)
@Getter
public class EscrowTransaction {

    private String transactionId;
    private String escrowId;
    private TransactionType type;
    private Money amount;
    private String note;
    private LocalDateTime createdAt;
}
