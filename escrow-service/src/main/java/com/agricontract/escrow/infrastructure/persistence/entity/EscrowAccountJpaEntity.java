package com.agricontract.escrow.infrastructure.persistence.entity;

import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escrow_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EscrowAccountJpaEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "escrow_id", nullable = false, unique = true)
    private String escrowId;

    @Column(name = "contract_id", nullable = false, unique = true)
    private String contractId;

    @Column(name = "buyer_user_id", nullable = false)
    private String buyerUserId;

    @Column(name = "seller_user_id", nullable = false)
    private String sellerUserId;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "seller_deposit", precision = 15, scale = 2)
    private BigDecimal sellerDeposit;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EscrowStatus status;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "escrowAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EscrowTransactionJpaEntity> transactions = new ArrayList<>();
}
