package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListContractsUseCase {

    private final ContractRepository contractRepository;

    public List<ContractResponse> executeByBuyer(String buyerId) {
        return contractRepository.findByBuyerId(buyerId).stream()
                .map(ContractResponse::from)
                .toList();
    }

    public List<ContractResponse> executeBySeller(String sellerId) {
        return contractRepository.findBySellerId(sellerId).stream()
                .map(ContractResponse::from)
                .toList();
    }
}
