package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ConfirmDepositCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.application.exception.UnauthorizedEscrowActionException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmDepositUseCase {
    private final EscrowAccountRepository escrowAccountRepository;

    public void execute(ConfirmDepositCommand command) {
        String contractId = command.contractId();

        EscrowAccount account = escrowAccountRepository.findByContractId(contractId)
                .orElseThrow(() -> new EscrowAccountNotFoundException(contractId));

        if (!account.getSellerUserId().equals(command.sellerUserId())) {
            throw new UnauthorizedEscrowActionException(command.sellerUserId(), contractId);
        }

        if (account.getStatus() == EscrowStatus.FULLY_LOCKED) {
            log.info("{} deposit already confirmed", contractId);
            return;
        }

        account.lockSellerDeposit();
        escrowAccountRepository.save(account);
    }
}
