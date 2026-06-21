package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ArbitrateEscrowCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.Money;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    }
}
