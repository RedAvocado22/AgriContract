package com.agricontract.contract.infrastructure.persistence.repository;

import com.agricontract.contract.domain.model.NegotiationRevision;
import com.agricontract.contract.domain.model.vo.ContractTerms;
import com.agricontract.contract.domain.model.vo.Money;
import com.agricontract.contract.domain.model.vo.Quantity;
import com.agricontract.contract.domain.repository.NegotiationHistoryRepository;
import com.agricontract.contract.infrastructure.persistence.entity.ContractNegotiationJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class NegotiationHistoryRepositoryImpl implements NegotiationHistoryRepository {

    private final ContractNegotiationJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NegotiationRevision> findByContractId(String contractId) {
        return jpaRepository.findByContractIdOrderByTermsRevisionAsc(contractId).stream()
                .map(this::toDomain)
                .toList();
    }

    private NegotiationRevision toDomain(ContractNegotiationJpaEntity entity) {
        ContractTerms terms = new ContractTerms(
                new Quantity(entity.getQuantity(), entity.getQuantityUnit()),
                new Money(entity.getAgreedPrice(), entity.getCurrency()),
                entity.getDeliveryDeadline(),
                entity.getBuyerPenaltyRate(),
                entity.getSellerDepositRate(),
                entity.getQualitySpec());
        return new NegotiationRevision(
                entity.getTermsRevision(), entity.getProposedBy(), entity.getProposedAt(), terms);
    }
}
