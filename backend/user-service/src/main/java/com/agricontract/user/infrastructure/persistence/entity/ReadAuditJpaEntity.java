package com.agricontract.user.infrastructure.persistence.entity;

import com.agricontract.user.domain.model.vo.AuditResourceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "kyc_read_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadAuditJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 255)
    private String actorId;

    @Column(name = "actor_role", nullable = false, updatable = false, length = 100)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false, length = 32)
    private AuditResourceType resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 255)
    private String resourceId;

    @Column(name = "case_context", updatable = false, length = 255)
    private String caseContext;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt;
}
