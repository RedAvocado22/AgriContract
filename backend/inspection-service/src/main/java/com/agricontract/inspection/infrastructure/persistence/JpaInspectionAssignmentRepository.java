package com.agricontract.inspection.infrastructure.persistence;

import com.agricontract.inspection.domain.repository.InspectionAssignmentRepository;
import com.agricontract.inspection.domain.model.InspectionAssignment;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JpaInspectionAssignmentRepository implements InspectionAssignmentRepository {
    private final AssignmentJpaRepository repository;

    public JpaInspectionAssignmentRepository(AssignmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<InspectionAssignment> findByCommissionId(String commissionId) {
        return repository.findByCommissionId(commissionId).map(InspectionAssignmentEntity::toDomain);
    }

    @Override
    public Optional<InspectionAssignment> findActive(String contractId, String milestoneId) {
        return repository.findByContractIdAndMilestoneIdAndEndedAtIsNull(contractId, milestoneId)
                .map(InspectionAssignmentEntity::toDomain);
    }
}
