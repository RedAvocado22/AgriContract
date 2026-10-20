package com.agricontract.user.infrastructure.persistence.entity;

import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 255)
    private String userId;

    @Column(name = "organization_name", nullable = false, length = 255)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "authorization_expires_at")
    private Instant authorizationExpiresAt;

    @Column(name = "verified_by_actor_id", length = 255)
    private String verifiedByActorId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Never in Hibernate's UPDATE: a KYC save of a stale entity would otherwise clear a lock written since it was loaded.
    @Column(name = "locked_until", updatable = false)
    private Instant lockedUntil;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
