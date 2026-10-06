package com.agricontract.escrow.domain.model;

import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.model.vo.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class EscrowTransactionTest {

    @Test
    void create_setsAllFieldsAndGeneratesIdAndTimestamp() {
        Money amount = new Money(new BigDecimal("100000"), "VND");

        EscrowTransaction transaction = EscrowTransaction.create(
                "escrow-1", TransactionType.LOCK, amount, "Lock buyer payment.");

        assertThat(transaction.getTransactionId()).isNotNull();
        assertThat(transaction.getEscrowId()).isEqualTo("escrow-1");
        assertThat(transaction.getType()).isEqualTo(TransactionType.LOCK);
        assertThat(transaction.getAmount()).isEqualTo(amount);
        assertThat(transaction.getNote()).isEqualTo("Lock buyer payment.");
        assertThat(transaction.getCreatedAt()).isNotNull();
    }

    @Test
    void create_calledTwice_generatesDifferentTransactionIds() {
        Money amount = new Money(new BigDecimal("100000"), "VND");

        EscrowTransaction t1 = EscrowTransaction.create("escrow-1", TransactionType.LOCK, amount, "note");
        EscrowTransaction t2 = EscrowTransaction.create("escrow-1", TransactionType.LOCK, amount, "note");

        assertThat(t1.getTransactionId()).isNotEqualTo(t2.getTransactionId());
    }
}
