package com.agricontract.contract.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "contract_negotiations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_contract_negotiation_revision",
                columnNames = {"contract_id", "terms_revision"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractNegotiationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false, length = 255)
    private String contractId;

    @Column(name = "terms_revision", nullable = false)
    private Integer termsRevision;

    @Column(name = "proposed_by", nullable = false, length = 255)
    private String proposedBy;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;

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
}
