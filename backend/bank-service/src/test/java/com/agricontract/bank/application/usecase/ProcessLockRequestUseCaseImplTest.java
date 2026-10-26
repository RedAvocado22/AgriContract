package com.agricontract.bank.application.usecase;

import com.agricontract.bank.application.dto.LockFundsCommand;
import com.agricontract.bank.application.port.LockResultPublisher;
import com.agricontract.bank.domain.exception.DuplicateLedgerEntryException;
import com.agricontract.bank.domain.model.EntryType;
import com.agricontract.bank.domain.model.FundType;
import com.agricontract.bank.domain.model.LedgerEntry;
import com.agricontract.bank.domain.model.Money;
import com.agricontract.bank.domain.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessLockRequestUseCaseImplTest {

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final RecordingResults results = new RecordingResults();
    private final ProcessLockRequestUseCaseImpl useCase = new ProcessLockRequestUseCaseImpl(ledger, results,
            new TransactionTemplate(new NoopTransactionManager()),
            Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneOffset.UTC));

    @Test
    void firstRequestRecordsOneEntryAndConfirmsIt() {
        LockFundsCommand command = buyerDeposit("40000000.00");
        useCase.execute(command);

        assertThat(ledger.entries).hasSize(1);
        assertThat(results.completed).containsExactly(ledger.entries.get(command.sourceEventId()).getEntryId());
        assertThat(results.failed).isEmpty();
    }

    @Test
    void repeatedRequestWritesNothingAndReconfirmsTheSameEntry() {
        LockFundsCommand command = buyerDeposit("40000000.00");
        useCase.execute(command);
        useCase.execute(command);

        assertThat(ledger.appends).isEqualTo(1);
        String entryId = ledger.entries.get(command.sourceEventId()).getEntryId();
        assertThat(results.completed).containsExactly(entryId, entryId);
    }

    @Test
    void sameSourceEventWithDifferentContentIsNotConfirmed() {
        LockFundsCommand command = buyerDeposit("40000000.00");
        useCase.execute(command);
        useCase.execute(withAmount(command, "50000000.00"));

        assertThat(ledger.appends).isEqualTo(1);
        assertThat(results.completed).hasSize(1);
        assertThat(results.failed).isEmpty();
    }

    @Test
    void ledgerRuleViolationIsAnsweredWithLockFailed() {
        // A milestone lock without its milestone: readable, but not a valid ledger row.
        String id = id();
        LockFundsCommand command = new LockFundsCommand(id, id(), id(), null, id(), EntryType.LOCK_MILESTONE,
                FundType.MILESTONE_PAYMENT, null, null, new BigDecimal("200000000.00"), id);
        useCase.execute(command);

        assertThat(ledger.entries).isEmpty();
        assertThat(results.failed).singleElement().asString().contains("needs a milestoneId");
    }

    @Test
    void losingAnInsertRaceReconfirmsTheWinnersEntry() {
        LockFundsCommand command = buyerDeposit("40000000.00");
        ledger.raceWinner = LedgerEntry.reconstitute(id(), command.sourceEventId(), command.contractId(), null,
                command.userId(), command.entryType(), command.fundType(), null, null,
                new Money(command.amount()), Instant.now());

        useCase.execute(command);

        assertThat(results.completed).containsExactly(ledger.raceWinner.getEntryId());
    }

    private static LockFundsCommand buyerDeposit(String amount) {
        String eventId = id();
        return new LockFundsCommand(eventId, id(), id(), null, id(), EntryType.LOCK_BUYER_DEPOSIT,
                FundType.BUYER_DEPOSIT, null, null, new BigDecimal(amount), eventId);
    }

    private static LockFundsCommand withAmount(LockFundsCommand c, String amount) {
        return new LockFundsCommand(c.requestEventId(), c.correlationId(), c.contractId(), c.milestoneId(),
                c.userId(), c.entryType(), c.fundType(), c.remedyDecisionId(), c.remedyLegId(),
                new BigDecimal(amount), c.sourceEventId());
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static final class InMemoryLedger implements LedgerEntryRepository {
        final Map<String, LedgerEntry> entries = new HashMap<>();
        int appends;
        // Set to simulate a concurrent delivery committing between our lookup and our insert.
        LedgerEntry raceWinner;

        @Override
        public void append(LedgerEntry entry) {
            if (raceWinner != null) {
                entries.put(raceWinner.getSourceEventId(), raceWinner);
                throw new DuplicateLedgerEntryException(entry.getSourceEventId());
            }
            if (entries.containsKey(entry.getSourceEventId())) {
                throw new DuplicateLedgerEntryException(entry.getSourceEventId());
            }
            appends++;
            entries.put(entry.getSourceEventId(), entry);
        }

        @Override
        public Optional<LedgerEntry> findBySourceEventId(String sourceEventId) {
            return Optional.ofNullable(entries.get(sourceEventId));
        }
    }

    private static final class RecordingResults implements LockResultPublisher {
        final List<String> completed = new ArrayList<>();
        final List<String> failed = new ArrayList<>();

        @Override
        public void completed(LockFundsCommand command, LedgerEntry entry) {
            completed.add(entry.getEntryId());
        }

        @Override
        public void failed(LockFundsCommand command, String reason) {
            failed.add(reason);
        }
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
