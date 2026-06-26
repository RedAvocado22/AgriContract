package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ConfirmDeliveryCommand;
import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.exception.UnauthorizedContractActionException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.model.vo.ContractStatus;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmDeliveryUseCase {

    private final ContractRepository contractRepository;

    public ContractResponse execute(ConfirmDeliveryCommand command) {
        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));

        if (!command.buyerId().equals(contract.getBuyerId())) {
            throw new UnauthorizedContractActionException("Only buyer can confirm delivery");
        }

        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new IllegalArgumentException("Contract must be ACTIVE to confirm delivery");
        }

        contract.confirmDelivery(command.buyerId());
        contractRepository.save(contract);
        log.info("Contract {} delivery confirmed by buyer {}", command.contractId(), command.buyerId());

        return ContractResponse.from(contract);
    }
}
