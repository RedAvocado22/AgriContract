package com.agricontract.product.infrastructure.persistence.entity;

import com.agricontract.product.domain.model.vo.ListingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

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

    @Column(name = "cover_image_url", nullable = false, length = 1000)
    private String coverImageUrl;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "quantity_unit", nullable = false, length = 50)
    private String quantityUnit;

    // Only written on insert; afterwards changed exclusively by conditional updates in ListingJpaRepository.
    // updatable = false so an entity save (close/expire) can never write back a stale counter.
    @Column(name = "quantity_available", precision = 15, scale = 3, updatable = false)
    private BigDecimal quantityAvailable;

    @Column(name = "min_order_quantity", precision = 15, scale = 3)
    private BigDecimal minOrderQuantity;

    @Column(name = "price_floor", nullable = false, precision = 15, scale = 2)
    private BigDecimal priceFloor;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "delivery_deadline", nullable = false)
    private LocalDate deliveryDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    // INVENTORY_EXHAUSTED when a reserve drained the listing; set/cleared only by conditional updates
    @Column(name = "close_reason", length = 30, insertable = false, updatable = false)
    private String closeReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // UTC, microsecond precision (matches DATETIME(6)) so the version token read back equals the stored value
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
