package com.agricontract.escrow.domain.repository;

import com.agricontract.escrow.domain.model.DepositLeg;

import java.time.Instant;
import java.util.List;

public interface DepositLegRepository {

    /** Inserts a new leg. A second leg for the same (contractId, party) violates uk_deposit_leg_contract_leg. */
    void insert(DepositLeg leg);

    List<DepositLeg> findByContractId(String contractId);

    /** Same, row-locked until the caller's transaction ends: serialises every result of one contract. */
    List<DepositLeg> findByContractIdForUpdate(String contractId);

    /** Writes the result fields (status, ledger entries, failures, next attempt, reason, return request, updatedAt). */
    void update(DepositLeg leg);

    /** Contracts with a lock (FAILED) or a return (RETURN_FAILED) whose resend is due. */
    List<String> findContractsWithRetryDue(Instant now, int limit);
}
