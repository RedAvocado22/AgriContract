package com.agricontract.product.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Idempotency-Key of an applied updateListing request (V11), unique per seller.
@Entity
@Table(name = "listing_edit_keys")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingEditKeyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, length = 255, updatable = false)
    private String sellerId;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "listing_id", nullable = false, length = 255, updatable = false)
    private String listingId;

    // SHA-256 hex of the request as the server read it
    @Column(name = "request_fingerprint", nullable = false, length = 64, columnDefinition = "char(64)", updatable = false)
    private String requestFingerprint;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
