package com.agricontract.escrow.infrastructure.persistence.entity;

import com.agricontract.escrow.domain.model.vo.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Append-only — never UPDATE, only INSERT
@Entity
@Table(name = "escrow_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EscrowTransactionJpaEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "escrow_id", nullable = false)
    private String escrowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 25)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
