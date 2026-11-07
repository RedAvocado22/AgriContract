package com.agricontract.contract.infrastructure.persistence.mapper;

import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.vo.*;
import com.agricontract.contract.infrastructure.persistence.entity.ContractJpaEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ContractMapper {

    private final ObjectMapper objectMapper;
    private final TermsCanonicalizer termsCanonicalizer;

    public ContractJpaEntity toJpaEntity(Contract contract) {
        ContractTerms terms = contract.getTerms();
        CanonicalTerms coffeeTerms = contract.getCoffeeTerms();
        return ContractJpaEntity.builder()
                .contractId(contract.getContractId().value())
                .listingId(contract.getListingId())
                .sellerId(contract.getSellerId())
                .buyerId(contract.getBuyerId())
                .productName(contract.getProductName())
                .buyerOrgName(contract.getBuyerOrgName())
                .sellerOrgName(contract.getSellerOrgName())
                .buyerEmail(contract.getBuyerEmail())
                .sellerEmail(contract.getSellerEmail())
                .quantity(terms.quantity().value())
                .quantityUnit(terms.quantity().unit())
                .agreedPrice(terms.agreedPrice().amount())
                .currency(terms.agreedPrice().currency())
                .deliveryDeadline(terms.deliveryDeadline())
                .buyerPenaltyRate(terms.buyerPenaltyRate())
                .sellerDepositRate(terms.sellerDepositRate())
                .qualitySpec(terms.qualitySpec())
                .termsSnapshot(coffeeTerms == null ? null : coffeeTerms.canonicalJson())
                .signedContentHash(coffeeTerms == null ? null : coffeeTerms.signedContentHash())
                .status(contract.getStatus())
                .signatories(serializeSignatories(contract.getSignatories()))
                .build();
    }

    public Contract toDomain(ContractJpaEntity e) {
        ContractTerms terms = new ContractTerms(
                new Quantity(e.getQuantity(), e.getQuantityUnit()),
                new Money(e.getAgreedPrice(), e.getCurrency()),
                e.getDeliveryDeadline(),
                e.getBuyerPenaltyRate(),
                e.getSellerDepositRate(),
                e.getQualitySpec()
        );
        return Contract.reconstitute(
                new ContractId(e.getContractId()),
                e.getListingId(),
                e.getBuyerId(),
                e.getSellerId(),
                e.getProductName(),
                e.getBuyerOrgName(),
                e.getSellerOrgName(),
                e.getBuyerEmail(),
                e.getSellerEmail(),
                terms,
                e.getTermsSnapshot() == null ? null
                        : CanonicalTerms.restore(e.getTermsSnapshot(), e.getSignedContentHash(), termsCanonicalizer),
                e.getStatus(),
                deserializeSignatories(e.getSignatories()),
                e.getVersion()
        );
    }

    private String serializeSignatories(Set<String> signatories) {
        try {
            return objectMapper.writeValueAsString(signatories);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize signatories", ex);
        }
    }

    private Set<String> deserializeSignatories(String json) {
        if (json == null || json.isBlank()) return new HashSet<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {});
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to deserialize signatories", ex);
        }
    }
}
