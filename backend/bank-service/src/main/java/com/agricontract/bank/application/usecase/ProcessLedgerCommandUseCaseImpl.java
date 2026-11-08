package com.agricontract.bank.application.usecase;

import com.agricontract.bank.application.dto.LedgerCommand;
import com.agricontract.bank.application.port.LedgerResultPublisher;
import com.agricontract.bank.domain.exception.DuplicateLedgerEntryException;
import com.agricontract.bank.domain.exception.InvalidLedgerEntryException;
import com.agricontract.bank.domain.model.LedgerEntry;
import com.agricontract.bank.domain.model.Money;
import com.agricontract.bank.domain.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;

/**
 * A bank command (lock, release or refund) → one ledger entry → its _completed result, or its _failed result when the
 * command breaks a ledger rule (bank design §3, §4). The bank is a "dumb writer": escrow validated conservation before
 * sending a release or refund (bank design §2b.5), so no balance is checked here. sourceEventId is the idempotency key: a repeated command writes nothing and re-sends the
 * confirmation of the entry already recorded, because the repeat may mean the first confirmation was lost.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessLedgerCommandUseCaseImpl implements ProcessLedgerCommandUseCase {

    private final LedgerEntryRepository ledger;
    private final LedgerResultPublisher results;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public void execute(LedgerCommand command) {
        var recorded = ledger.findBySourceEventId(command.sourceEventId());
        if (recorded.isPresent()) {
            replay(command, recorded.get());
            return;
        }

        LedgerEntry entry;
        try {
            entry = LedgerEntry.create(command.sourceEventId(), command.contractId(), command.milestoneId(),
                    command.userId(), command.entryType(), command.fundType(), command.remedyDecisionId(),
                    command.remedyLegId(), new Money(command.amount()), clock.instant());
        } catch (InvalidLedgerEntryException e) {
            log.warn("event=bank_command_rejected operation={} sourceEventId={} contractId={} reason={}",
                    command.operation(), command.sourceEventId(), command.contractId(), e.getMessage());
            transactionTemplate.executeWithoutResult(status -> results.failed(command, e.getMessage()));
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                ledger.append(entry);
                results.completed(command, entry);
            });
            log.info("event=bank_entry_recorded sourceEventId={} contractId={} entryType={} ledgerEntryId={}",
                    command.sourceEventId(), command.contractId(), command.entryType(), entry.getEntryId());
        } catch (DuplicateLedgerEntryException e) {
            // A concurrent delivery committed first; its entry is the answer for this one too.
            replay(command, ledger.findBySourceEventId(command.sourceEventId()).orElseThrow(() -> e));
        }
    }

    private void replay(LedgerCommand command, LedgerEntry recorded) {
        if (!sameCommand(command, recorded)) {
            // Same sourceEventId, different content: a producer bug or an edited replay. Confirming it would tell
            // escrow an amount was locked that never was, so nothing is sent.
            log.error("event=bank_command_replay_mismatch sourceEventId={} contractId={} ledgerEntryId={}",
                    command.sourceEventId(), command.contractId(), recorded.getEntryId());
            return;
        }
        log.info("event=bank_command_replayed sourceEventId={} ledgerEntryId={}",
                command.sourceEventId(), recorded.getEntryId());
        transactionTemplate.executeWithoutResult(status -> results.completed(command, recorded));
    }

    private static boolean sameCommand(LedgerCommand command, LedgerEntry recorded) {
        return command.contractId().equals(recorded.getContractId())
                && Objects.equals(command.milestoneId(), recorded.getMilestoneId())
                && command.userId().equals(recorded.getUserId())
                && command.entryType() == recorded.getEntryType()
                && command.fundType() == recorded.getFundType()
                && Objects.equals(command.remedyDecisionId(), recorded.getRemedyDecisionId())
                && Objects.equals(command.remedyLegId(), recorded.getRemedyLegId())
                && command.amount().compareTo(recorded.getAmount().amount()) == 0;
    }
}
