package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.activation.ActivationRecovery;

import java.util.Optional;

public interface ActivationRecoveryRepository {

    Optional<ActivationRecovery> findByContractId(String contractId);

    /** Insert the first failure or update the latest one; called with the contract row locked. */
    void save(ActivationRecovery recovery);
}
