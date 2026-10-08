package com.agricontract.inspection.infrastructure.persistence;

import org.springframework.data.repository.Repository;
import java.util.Optional;

interface AssignmentJpaRepository extends Repository<InspectionAssignmentEntity, String> {
    Optional<InspectionAssignmentEntity> findByCommissionId(String commissionId);
    Optional<InspectionAssignmentEntity> findByContractIdAndMilestoneIdAndEndedAtIsNull(
            String contractId, String milestoneId);
}
