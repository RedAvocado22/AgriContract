package com.agricontract.escrow.domain.repository;

import com.agricontract.escrow.domain.model.DepositLeg;

import java.util.List;

public interface DepositLegRepository {

    /** Inserts a new leg. A second leg for the same (contractId, party) violates uk_deposit_leg_contract_leg. */
    void insert(DepositLeg leg);

    List<DepositLeg> findByContractId(String contractId);
}
