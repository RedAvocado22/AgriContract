package com.agricontract.contract.application.usecase;

import com.agricontract.contract.application.dto.SignContractCommand;
import com.agricontract.contract.application.exception.ContractNotFoundException;
import com.agricontract.contract.application.port.ListingPort;
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
public class SignContractUseCase {
    private final ContractRepository contractRepository;
    private final ListingPort listingPort;

    public void execute(SignContractCommand command) {
        Contract contract = contractRepository.findById(new ContractId(command.contractId()))
                .orElseThrow(() -> new ContractNotFoundException(command.contractId()));


        if (contract.getStatus() != ContractStatus.OFFERED && contract.getStatus() != ContractStatus.NEGOTIATING) {
            throw new IllegalArgumentException("Invalid contract status");
        }

        if (contract.getSignatories().contains(command.userId())) {
            throw new IllegalArgumentException("This user already has a signatory for this contract");
        }

        contract.sign(command.userId());

        contractRepository.save(contract);

        if (contract.getStatus() == ContractStatus.SIGNED) {
            log.info("Contract {} fully SIGNED, closing listing {}", command.contractId(), contract.getListingId());
            try {
                listingPort.closeListing(contract.getListingId());
            } catch (Exception e) {
                log.error("Contract {} SIGNED but failed to close listing {}: {} — needs manual fix",
                        command.contractId(), contract.getListingId(), e.getMessage(), e);
            }
        } else {
            log.info("Contract {} partially signed by {}", command.contractId(), command.userId());
        }
    }
}
