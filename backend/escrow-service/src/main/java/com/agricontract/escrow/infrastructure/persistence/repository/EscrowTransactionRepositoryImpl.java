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

    @Override
    @Transactional(readOnly = true)
    public List<EscrowTransaction> findByEscrowId(String escrowId) {
        return jpaRepo.findByEscrowId(escrowId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
