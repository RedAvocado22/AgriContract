package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.PenalizeEscrowCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.model.vo.Party;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PenalizeEscrowUseCase {
    private final EscrowAccountRepository escrowAccountRepository;

    public void execute(PenalizeEscrowCommand command) {
        String contractId = command.contractId();

        EscrowAccount account = escrowAccountRepository.findByContractId(contractId)
                .orElseThrow(() -> new EscrowAccountNotFoundException(contractId));

        if (account.getStatus() == EscrowStatus.PENALIZED_BUYER || account.getStatus() == EscrowStatus.PENALIZED_SELLER) {
            log.info("{} already penalized, status={}", contractId, account.getStatus());
            return;
        }

        if (command.cancelledBy() == Party.BUYER) {
            account.penalizeBuyer(command.buyerPenaltyRate());
        } else {
            account.penalizeSeller();
        }

        escrowAccountRepository.save(account);
    }
}
