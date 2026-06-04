package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;

import java.util.List;
import java.util.Optional;

public interface ContractRepository {
    Contract save(Contract contract);
    Optional<Contract> findById(ContractId contractId);
    List<Contract> findByBuyerId(String buyerId);
    List<Contract> findBySellerId(String sellerId);
    List<Contract> findByStatus(ContractStatus status);
}
