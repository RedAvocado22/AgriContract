package com.agricontract.escrow.infrastructure.persistence.repository;

import com.agricontract.escrow.domain.model.EscrowTransaction;
import com.agricontract.escrow.domain.repository.EscrowTransactionRepository;
import com.agricontract.escrow.infrastructure.persistence.mapper.EscrowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EscrowTransactionRepositoryImpl implements EscrowTransactionRepository {

    private final EscrowTransactionJpaRepository jpaRepo;
    private final EscrowMapper mapper;

    // EscrowTransaction is not persisted standalone — it's part of the EscrowAccount
    // aggregate and is cascade-saved through EscrowAccountRepository.save().
    @Override
    public EscrowTransaction save(EscrowTransaction transaction) {
        throw new UnsupportedOperationException(
                "EscrowTransaction is persisted via EscrowAccountRepository.save() as part of its aggregate");
    }

    @Override
    @Transactional(readOnly = true)
    public List<EscrowTransaction> findByEscrowId(String escrowId) {
        return jpaRepo.findByEscrowId(escrowId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
