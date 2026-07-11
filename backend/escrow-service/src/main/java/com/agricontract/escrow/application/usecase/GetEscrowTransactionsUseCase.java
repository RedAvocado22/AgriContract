package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.EscrowTransactionResponse;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.application.exception.UnauthorizedEscrowActionException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowId;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetEscrowTransactionsUseCase {
    private final EscrowAccountRepository escrowAccountRepository;

    public List<EscrowTransactionResponse> execute(String escrowId, String requesterId, boolean isAdmin) {
        EscrowAccount account = escrowAccountRepository.findById(new EscrowId(escrowId))
                .orElseThrow(() -> EscrowAccountNotFoundException.forEscrowId(escrowId));

        if (!isAdmin && !account.getBuyerUserId().equals(requesterId) && !account.getSellerUserId().equals(requesterId)) {
            throw new UnauthorizedEscrowActionException(requesterId, account.getContractId());
        }

        return account.getTransactions().stream()
                .map(tx -> new EscrowTransactionResponse(
                        tx.getTransactionId().toString(),
                        tx.getType(),
                        tx.getAmount().amount(),
                        tx.getAmount().currency(),
                        tx.getNote(),
                        tx.getCreatedAt()
                ))
                .toList();
    }
}
