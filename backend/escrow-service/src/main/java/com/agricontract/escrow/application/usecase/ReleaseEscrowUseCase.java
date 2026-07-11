package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ReleaseEscrowCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseEscrowUseCase {
    private final EscrowAccountRepository escrowAccountRepository;

    public void execute(ReleaseEscrowCommand command) {
        String contractId = command.contractId();

        EscrowAccount account = escrowAccountRepository.findByContractId(contractId)
                .orElseThrow(() -> new EscrowAccountNotFoundException(contractId));

        if (account.getStatus() == EscrowStatus.RELEASED) {
            log.info("{} has been released", contractId);
            return;
        }

        account.release();
        escrowAccountRepository.save(account);
    }
}
