package com.agricontract.inspection.infrastructure.persistence;

import com.agricontract.inspection.domain.model.InspectionAssignment;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "level1_5_inspection_assignment")
public class InspectionAssignmentEntity {
    @Id
    @Column(name = "commission_id", columnDefinition = "char(36)")
    private String commissionId;
    @Column(name = "contract_id", nullable = false, columnDefinition = "char(36)")
    private String contractId;
    @Column(name = "milestone_id", nullable = false, columnDefinition = "char(36)")
    private String milestoneId;
    @Column(name = "inspector_id", nullable = false, columnDefinition = "char(36)")
    private String inspectorId;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "assigned_by_service", nullable = false, length = 64)
    private String assignedByService;
    @Column(name = "initiated_by_actor_id", columnDefinition = "char(36)")
    private String initiatedByActorId;

    protected InspectionAssignmentEntity() { }

    public InspectionAssignment toDomain() {
        return new InspectionAssignment(commissionId, contractId, milestoneId, inspectorId,
                assignedAt, endedAt, assignedByService, initiatedByActorId);
    }
}
