package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.dto.PagedResult;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.domain.repository.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListContractsUseCase {

    private final ContractRepository contractRepository;

    public PagedResult<ContractResponse> executeByBuyer(String buyerId, ContractStatus status, PageQuery page) {
        List<Contract> contracts = contractRepository.findByBuyerId(buyerId, status, page);
        long total = contractRepository.countByBuyerId(buyerId, status);
        return toPagedResult(contracts, page, total);
    }

    public PagedResult<ContractResponse> executeBySeller(String sellerId, ContractStatus status, PageQuery page) {
        List<Contract> contracts = contractRepository.findBySellerId(sellerId, status, page);
        long total = contractRepository.countBySellerId(sellerId, status);
        return toPagedResult(contracts, page, total);
    }

    private PagedResult<ContractResponse> toPagedResult(List<Contract> contracts, PageQuery page, long total) {
        List<ContractResponse> content = contracts.stream().map(ContractResponse::from).toList();
        int totalPages = (int) Math.ceil((double) total / page.size());
        int pageNum = page.page();
        return new PagedResult<>(
                content,
                pageNum,
                page.size(),
                total,
                totalPages,
                pageNum == 0,
                totalPages == 0 || pageNum >= totalPages - 1,
                content.isEmpty()
        );
    }
}
