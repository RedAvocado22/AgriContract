package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.NegotiationHistoryResponse;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ContractRepository;
import com.agricontract.contract.domain.repository.NegotiationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetNegotiationHistoryUseCase {

    private final ContractRepository contractRepository;
    private final NegotiationHistoryRepository negotiationHistoryRepository;

    public List<NegotiationHistoryResponse> execute(String contractId, String requesterId, boolean isAdmin) {
        Contract contract = contractRepository.findById(new ContractId(contractId))
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (!isAdmin
                && !requesterId.equals(contract.getBuyerId())
                && !requesterId.equals(contract.getSellerId())) {
            throw new UnauthorizedContractActionException("Access denied");
        }

        return negotiationHistoryRepository.findByContractId(contractId).stream()
                .map(NegotiationHistoryResponse::from)
                .toList();
    }
}
