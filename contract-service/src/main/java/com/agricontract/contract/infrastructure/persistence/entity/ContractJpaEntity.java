package com.agricontract.contract.infrastructure.persistence.entity;

import com.agricontract.contract.domain.model.vo.CancelledBy;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contracts")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false, unique = true, length = 255)
    private String contractId;

    @Column(name = "listing_id", nullable = false, length = 255)
    private String listingId;

    @Column(name = "seller_id", nullable = false, length = 255)
    private String sellerId;

    @Column(name = "buyer_id", nullable = false, length = 255)
    private String buyerId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "buyer_org_name", nullable = false, length = 255)
    private String buyerOrgName;

    @Column(name = "seller_org_name", nullable = false, length = 255)
    private String sellerOrgName;

    @Column(name = "buyer_email", nullable = false, length = 255)
    private String buyerEmail;

    @Column(name = "seller_email", nullable = false, length = 255)
    private String sellerEmail;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "quantity_unit", nullable = false, length = 50)
    private String quantityUnit;

    @Column(name = "agreed_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal agreedPrice;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "delivery_deadline", nullable = false)
    private LocalDate deliveryDeadline;

    @Column(name = "buyer_penalty_rate", precision = 10, scale = 4)
    private BigDecimal buyerPenaltyRate;

    @Column(name = "seller_deposit_rate", precision = 10, scale = 4)
    private BigDecimal sellerDepositRate;

    @Column(name = "quality_spec", columnDefinition = "TEXT")
    private String qualitySpec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 10)
    private CancelledBy cancelledBy;

    @Column(columnDefinition = "TEXT")
    private String signatories;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
