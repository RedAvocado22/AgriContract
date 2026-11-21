package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.milestone.Milestone;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Milestones (milestone-escrow design §2.2): one row per batch, saved apart from the Contract row. */
public interface MilestoneRepository {

    /** The milestones of a contract that just became ACTIVE. Fails if the contract already has any. */
    void insertAll(List<Milestone> milestones);

    /** Persists a changed milestone; must hold the row lock taken by {@link #findByIdForUpdate}. */
    void update(Milestone milestone);

    Optional<Milestone> findByIdForUpdate(String milestoneId);

    List<Milestone> findByContractId(String contractId);

    Optional<String> findContractIdOf(String milestoneId);

    /**
     * Ids of NOT_REQUESTED milestones of ACTIVE contracts whose fundingScheduledDate is on or before {@code today},
     * oldest date first.
     */
    List<String> findIdsDueForFunding(LocalDate today, int limit);
}
