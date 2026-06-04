package com.agricontract.escrow.domain.repository;

import com.agricontract.escrow.domain.model.EscrowTransaction;

import java.util.List;

public interface EscrowTransactionRepository {
    EscrowTransaction save(EscrowTransaction transaction);
    List<EscrowTransaction> findByEscrowId(String escrowId);
}
