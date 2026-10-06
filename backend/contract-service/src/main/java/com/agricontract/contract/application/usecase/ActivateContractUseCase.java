package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ActivateContractCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
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
public class ActivateContractUseCase {
    private final ContractRepository contractRepository;

    public void execute(ActivateContractCommand command) {
        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException("Contract not found"));

        if (contract.getStatus() == ContractStatus.ACTIVE) {
            log.info("Contract {} already ACTIVE, skipping duplicate escrow.locked event", command.contractId());
            return;
        }

        contract.activate();
        contractRepository.save(contract);
        log.info("Contract {} activated", command.contractId());
    }
}
