package com.agricontract.user.infrastructure.persistence.entity;

import com.agricontract.user.domain.model.vo.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "role_change_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleChangeAuditJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 255)
    private String actorId;

    @Column(name = "target_user_id", nullable = false, updatable = false, length = 255)
    private String targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_role", nullable = false, updatable = false, length = 32)
    private Role oldRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", nullable = false, updatable = false, length = 32)
    private Role newRole;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
