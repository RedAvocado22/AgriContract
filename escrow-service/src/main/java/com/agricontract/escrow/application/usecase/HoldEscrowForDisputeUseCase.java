package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.HoldEscrowForDisputeCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HoldEscrowForDisputeUseCase {

    private final EscrowAccountRepository escrowAccountRepository;

    public void execute(HoldEscrowForDisputeCommand command) {
        EscrowAccount account = escrowAccountRepository.findByContractId(command.contractId())
                .orElseThrow(() -> new EscrowAccountNotFoundException(command.contractId()));

        if (account.getStatus() == EscrowStatus.DISPUTED
                || account.getStatus() == EscrowStatus.ARBITRATED) {
            log.info("{} already held or arbitrated, skipping duplicate dispute event", command.contractId());
            return;
        }

        account.holdForDispute();
        escrowAccountRepository.save(account);
    }
}
