package com.agricontract.escrow.infrastructure.persistence.mapper;

import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.EscrowTransaction;
import com.agricontract.escrow.domain.model.vo.EscrowId;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.infrastructure.persistence.entity.EscrowAccountJpaEntity;
import com.agricontract.escrow.infrastructure.persistence.entity.EscrowTransactionJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class EscrowMapper {

    public EscrowAccountJpaEntity toJpaEntity(EscrowAccount account) {
        EscrowAccountJpaEntity entity = EscrowAccountJpaEntity.builder()
                .escrowId(account.getEscrowId().value())
                .contractId(account.getContractId())
                .buyerUserId(account.getBuyerUserId())
                .sellerUserId(account.getSellerUserId())
                .totalAmount(account.getTotalAmount().amount())
                .sellerDeposit(account.getSellerDeposit() != null ? account.getSellerDeposit().amount() : null)
                .sellerDepositRate(account.getSellerDepositRate())
                .currency(account.getTotalAmount().currency())
                .status(account.getStatus())
                .build();

        List<EscrowTransactionJpaEntity> transactions = account.getTransactions().stream()
                .map(tx -> toJpaEntity(tx, entity))
                .toList();
        entity.setTransactions(transactions);

        return entity;
    }

    public EscrowAccount toDomain(EscrowAccountJpaEntity entity) {
        Money totalAmount = new Money(entity.getTotalAmount(), entity.getCurrency());
        Money sellerDeposit = entity.getSellerDeposit() != null
                ? new Money(entity.getSellerDeposit(), entity.getCurrency())
                : null;

        List<EscrowTransaction> transactions = entity.getTransactions().stream()
                .map(this::toDomain)
                .toList();

        return EscrowAccount.reconstitute(
                new EscrowId(entity.getEscrowId()),
                entity.getContractId(),
                entity.getBuyerUserId(),
                entity.getSellerUserId(),
                totalAmount,
                sellerDeposit,
                entity.getStatus(),
                entity.getSellerDepositRate(),
                transactions
        );
    }

    private EscrowTransactionJpaEntity toJpaEntity(EscrowTransaction tx, EscrowAccountJpaEntity parent) {
        return EscrowTransactionJpaEntity.builder()
                .transactionId(tx.getTransactionId().toString())
                .escrowAccount(parent)
                .escrowId(tx.getEscrowId())
                .transactionType(tx.getType())
                .amount(tx.getAmount().amount())
                .currency(tx.getAmount().currency())
                .note(tx.getNote())
                .build();
    }

    public EscrowTransaction toDomain(EscrowTransactionJpaEntity entity) {
        return EscrowTransaction.reconstitute(
                UUID.fromString(entity.getTransactionId()),
                entity.getEscrowId(),
                entity.getTransactionType(),
                new Money(entity.getAmount(), entity.getCurrency()),
                entity.getNote(),
                entity.getCreatedAt()
        );
    }
}
