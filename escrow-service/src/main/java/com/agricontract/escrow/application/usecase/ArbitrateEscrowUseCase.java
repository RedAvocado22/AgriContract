package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ArbitrateEscrowCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrateEscrowUseCase {
    private final EscrowAccountRepository escrowAccountRepository;

    public void execute(ArbitrateEscrowCommand command) {
        EscrowAccount account = escrowAccountRepository.findByContractId(command.contractId())
                .orElseThrow(() -> new EscrowAccountNotFoundException(command.contractId()));

        String currency = account.getTotalAmount().currency();
        Money buyerAmount = new Money(command.buyerAmount(), currency);
        Money sellerAmount = new Money(command.sellerAmount(), currency);

        account.arbitrate(buyerAmount, sellerAmount, command.justification());
        escrowAccountRepository.save(account);
        log.info("{} arbitrated: buyer={}, seller={}, justification={}",
                command.contractId(), buyerAmount, sellerAmount, command.justification());
    }
}
