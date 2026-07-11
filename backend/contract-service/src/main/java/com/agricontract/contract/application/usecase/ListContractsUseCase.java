package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.dto.PagedResult;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListContractsUseCase {

    private final ContractRepository contractRepository;

    public PagedResult<ContractResponse> executeByBuyer(String buyerId, ContractStatus status, Pageable pageable) {
        List<Contract> contracts = contractRepository.findByBuyerId(buyerId, status, pageable);
        long total = contractRepository.countByBuyerId(buyerId, status);
        return toPagedResult(contracts, pageable, total);
    }

    public PagedResult<ContractResponse> executeBySeller(String sellerId, ContractStatus status, Pageable pageable) {
        List<Contract> contracts = contractRepository.findBySellerId(sellerId, status, pageable);
        long total = contractRepository.countBySellerId(sellerId, status);
        return toPagedResult(contracts, pageable, total);
    }

    private PagedResult<ContractResponse> toPagedResult(List<Contract> contracts, Pageable pageable, long total) {
        List<ContractResponse> content = contracts.stream().map(ContractResponse::from).toList();
        int totalPages = (int) Math.ceil((double) total / pageable.getPageSize());
        int pageNum = pageable.getPageNumber();
        return new PagedResult<>(
                content,
                pageNum,
                pageable.getPageSize(),
                total,
                totalPages,
                pageNum == 0,
                totalPages == 0 || pageNum >= totalPages - 1,
                content.isEmpty()
        );
    }
}
