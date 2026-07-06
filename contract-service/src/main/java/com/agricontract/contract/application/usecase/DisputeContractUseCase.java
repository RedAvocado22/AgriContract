package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.ContractResponse;
import com.agricontract.contract.application.dto.DisputeContractCommand;
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
public class DisputeContractUseCase {

    private final ContractRepository contractRepository;

    public ContractResponse execute(DisputeContractCommand command) {
        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));

        contract.dispute(command.buyerId(), command.reason());
        contractRepository.save(contract);
        log.info("Contract {} disputed by buyer {}: {}", command.contractId(), command.buyerId(), command.reason());

        return ContractResponse.from(contract);
    }
}
