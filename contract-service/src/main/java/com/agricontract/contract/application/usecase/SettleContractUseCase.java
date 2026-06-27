package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.SettleContractCommand;
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
public class SettleContractUseCase {

    private final ContractRepository contractRepository;

    public void execute(SettleContractCommand command) {
        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));

        if (contract.getStatus() == ContractStatus.SETTLED) {
            log.info("Contract {} already SETTLED, skipping duplicate escrow.released event", command.contractId());
            return;
        }

        contract.settle();
        contractRepository.save(contract);
    }
}
