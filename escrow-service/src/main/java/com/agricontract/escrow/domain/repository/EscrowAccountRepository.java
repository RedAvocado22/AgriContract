package com.agricontract.escrow.domain.repository;

import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowId;

import java.util.Optional;

public interface EscrowAccountRepository {
    EscrowAccount save(EscrowAccount account);
    Optional<EscrowAccount> findById(EscrowId escrowId);
    Optional<EscrowAccount> findByContractId(String contractId);
    boolean existsByContractId(String contractId);
}
