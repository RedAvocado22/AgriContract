package com.agricontract.escrow.domain.repository;

import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowId;

import java.util.Optional;
import java.util.List;
import java.time.Instant;

public interface EscrowAccountRepository {
    EscrowAccount save(EscrowAccount account);
    Optional<EscrowAccount> findById(EscrowId escrowId);
    Optional<EscrowAccount> findByContractId(String contractId);
    boolean existsByContractId(String contractId);
    List<EscrowAccount> findReleaseEligibleBefore(Instant threshold);
}
