package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.TransactionType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

// Entity — append-only financial ledger (never updated, only inserted)
@Getter
public class EscrowTransaction {

    private UUID transactionId;
    private String escrowId;
    private TransactionType type;
    private Money amount;
    private String note;
    private LocalDateTime createdAt;

    private EscrowTransaction() {
        this.transactionId = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
    }

    public static EscrowTransaction create(String escrowId, TransactionType type, Money amount, String note) {
        EscrowTransaction transaction = new EscrowTransaction();
        
        transaction.escrowId = escrowId;
        transaction.type = type;
        transaction.amount = amount;
        transaction.note = note;

        return transaction;
    }
}
