package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.LockBuyerPaymentCommand;
import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LockBuyerPaymentUseCase {
    private final EscrowAccountRepository escrowAccountRepository;

    public void execute(LockBuyerPaymentCommand command) {
        if (command.contractId() == null || command.buyerId() == null || command.sellerId() == null) {
            throw new InvalidEventPayloadException("contractId/buyerId/sellerId must not be null: " + command);
        }

        if (escrowAccountRepository.existsByContractId(command.contractId())) {
            log.info("EscrowAccount already exists for contract {}, skipping duplicate contract.signed event", command.contractId());
            return;
        }

        EscrowAccount account = EscrowAccount.lockBuyerPayment(
                command.contractId(), command.buyerId(), command.sellerId(),
                command.buyerEmail(), command.sellerEmail(),
                command.sellerDepositRate(), command.agreedPrice()
        );

        escrowAccountRepository.save(account);
        log.info("EscrowAccount created for contract {}, buyer payment locked", command.contractId());
    }
}
