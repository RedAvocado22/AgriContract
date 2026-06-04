package com.agricontract.product.infrastructure.persistence.entity;

import com.agricontract.product.domain.model.vo.ListingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "listings")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false, unique = true, length = 255)
    private String listingId;

    @Column(name = "seller_id", nullable = false, length = 255)
    private String sellerId;

    @Column(name = "product_id", nullable = false, length = 255)
    private String productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "quantity_unit", nullable = false, length = 50)
    private String quantityUnit;

    @Column(name = "price_floor", nullable = false, precision = 15, scale = 2)
    private BigDecimal priceFloor;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "delivery_deadline", nullable = false)
    private LocalDate deliveryDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
