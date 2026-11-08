package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ActivationRecoveryView;
import com.agricontract.contract.application.dto.CoffeeContractView;
import com.agricontract.contract.application.dto.ContractDetail;
import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ActivationRecoveryRepository;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetContractUseCase {

    private final ContractRepository contractRepository;
    private final ActivationRecoveryRepository activationRecoveries;

    public ContractDetail execute(String contractId, String requesterId, boolean isAdmin) {
        Contract contract = contractRepository.findById(new ContractId(contractId))
                .orElseThrow(() -> new ContractNotFoundException(contractId));

        if (!isAdmin && !requesterId.equals(contract.getBuyerId()) && !requesterId.equals(contract.getSellerId())) {
            throw new UnauthorizedContractActionException("Access denied");
        }

        return contract.getCoffeeTerms() != null
                ? new ContractDetail(CoffeeContractView.from(contract).withActivationRecovery(
                        activationRecoveries.findByContractId(contractId).map(ActivationRecoveryView::from)
                                .orElse(null)), null)
                : new ContractDetail(null, ContractResponse.from(contract));
    }
}
