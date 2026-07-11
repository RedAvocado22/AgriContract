package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ContractRepository {
    Contract save(Contract contract);
    Optional<Contract> findById(ContractId contractId);
    List<Contract> findByBuyerId(String buyerId);
    List<Contract> findBySellerId(String sellerId);
    List<Contract> findByStatus(ContractStatus status);
    List<Contract> findByBuyerId(String buyerId, ContractStatus status, Pageable pageable);
    List<Contract> findBySellerId(String sellerId, ContractStatus status, Pageable pageable);
    long countByBuyerId(String buyerId, ContractStatus status);
    long countBySellerId(String sellerId, ContractStatus status);
}
