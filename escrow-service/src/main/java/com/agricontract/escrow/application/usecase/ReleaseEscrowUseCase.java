package com.agricontract.escrow.application.usecase;

import com.agricontract.escrow.application.dto.ReleaseEscrowCommand;
import com.agricontract.escrow.application.exception.EscrowAccountNotFoundException;
import com.agricontract.escrow.domain.model.EscrowAccount;
import com.agricontract.escrow.domain.model.vo.EscrowStatus;
import com.agricontract.escrow.domain.repository.EscrowAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;

@Slf4j
@Service
public class ReleaseEscrowUseCase {
    private final EscrowAccountRepository escrowAccountRepository;
    private final Duration disputeWindow;
    private final Clock clock;

    @Autowired
    public ReleaseEscrowUseCase(
            EscrowAccountRepository escrowAccountRepository,
            @Value("${escrow.delivery.dispute-window:PT30S}") Duration disputeWindow) {
        this(escrowAccountRepository, disputeWindow, Clock.systemUTC());
    }

    ReleaseEscrowUseCase(
            EscrowAccountRepository escrowAccountRepository,
            Duration disputeWindow,
            Clock clock) {
        this.escrowAccountRepository = escrowAccountRepository;
        this.disputeWindow = disputeWindow;
        this.clock = clock;
    }

    public void execute(ReleaseEscrowCommand command) {
        String contractId = command.contractId();

        EscrowAccount account = escrowAccountRepository.findByContractId(contractId)
                .orElseThrow(() -> new EscrowAccountNotFoundException(contractId));

        if (account.getStatus() == EscrowStatus.RELEASED
                || account.getStatus() == EscrowStatus.DELIVERY_PENDING) {
            log.info("{} delivery release already scheduled or completed", contractId);
            return;
        }

        if (account.getStatus() == EscrowStatus.DISPUTED
                || account.getStatus() == EscrowStatus.ARBITRATED) {
            log.info("{} is disputed or arbitrated; delivery event cannot release funds", contractId);
            return;
        }

        account.scheduleRelease(clock.instant().plus(disputeWindow));
        escrowAccountRepository.save(account);
    }
}
