package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.CancelContractCommand;
import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.domain.model.Contract;
import com.agricontract.contract.domain.model.vo.ContractId;
import com.agricontract.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancelContractUseCase {

    private final ContractRepository contractRepository;

    public ContractResponse execute(CancelContractCommand command) {
        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));

        contract.cancel(command.userId(), command.reason());
        contractRepository.save(contract);
        log.info("Contract {} cancelled by {}: {}", command.contractId(), command.userId(), command.reason());

        return ContractResponse.from(contract);
    }
}
