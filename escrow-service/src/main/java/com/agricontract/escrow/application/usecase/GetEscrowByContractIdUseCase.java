package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.EscrowAccountResponse;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetEscrowByContractIdUseCase {
    private final EscrowAccountRepository escrowAccountRepository;

    public EscrowAccountResponse execute(String contractId) {
        EscrowAccount account = escrowAccountRepository.findByContractId(contractId)
                .orElseThrow(() -> new EscrowAccountNotFoundException(contractId));

        return new EscrowAccountResponse(
                account.getEscrowId().value(),
                account.getContractId(),
                account.getBuyerUserId(),
                account.getSellerUserId(),
                account.getTotalAmount().amount(),
                account.getSellerDeposit() != null ? account.getSellerDeposit().amount() : null,
                account.getTotalAmount().currency(),
                account.getStatus()
        );
    }
}
