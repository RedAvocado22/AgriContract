package com.agricontract.bank.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agricontract.bank.domain.exception.InvalidLedgerEntryException;
import com.agricontract.bank.domain.model.EntryType;
import com.agricontract.bank.domain.model.FundType;
import com.agricontract.bank.domain.model.LedgerEntry;
import com.agricontract.bank.domain.model.Money;
import com.agricontract.bank.infrastructure.persistence.JdbcLedgerEntryRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/** Ledger invariants enforced by the real database: production migration plus the actual runtime grants. */
class LedgerSchemaMySqlTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42")
            .withDatabaseName("bank_db").withUsername("bank_migration")
            .withPassword("test-only-migration-password").withEnv("MYSQL_ROOT_HOST", "%");
    static JdbcTemplate runtime;

    @BeforeAll
    static void setUp() throws Exception {
        MYSQL.start(); // No disabled-without-Docker condition: missing integration infrastructure is a failure.
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        admin.execute("CREATE USER 'bank_runtime'@'%' IDENTIFIED BY 'test-only-runtime-password'");
        String sql = Files.readString(Path.of("ops/grants.sql")).replaceAll("(?m)--[^\\r\\n]*", "");
        for (String statement : sql.split(";")) if (!statement.isBlank()) admin.execute(statement);
        runtime = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "bank_runtime", "test-only-runtime-password"));
    }

    @Test
    void runsAsTheRestrictedRuntimeAccount() {
        assertThat(runtime.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo("bank_runtime@%");
    }

    @Test
    void acceptsAValidDepositLock() {
        String entryId = insert(UUID.randomUUID().toString(), "LOCK_BUYER_DEPOSIT", "BUYER_DEPOSIT", null, null, null, "1500000.00");
        assertThat(runtime.queryForObject("SELECT amount FROM ledger_entry WHERE entry_id = ?", BigDecimal.class, entryId))
                .isEqualByComparingTo("1500000.00");
    }

    @Test
    void repositoryAppendsAnEntryAsTheRuntimeAccount() {
        String entryId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-10-27T03:00:00.123456Z");
        new JdbcLedgerEntryRepository(runtime).append(LedgerEntry.reconstitute(entryId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                EntryType.LOCK_MILESTONE, FundType.MILESTONE_PAYMENT, null, null,
                new Money(new BigDecimal("2500000.00")), createdAt));
        assertThat(runtime.queryForObject("SELECT amount FROM ledger_entry WHERE entry_id = ?", BigDecimal.class, entryId))
                .isEqualByComparingTo("2500000.00");
        assertThat(runtime.queryForObject("SELECT created_at FROM ledger_entry WHERE entry_id = ?",
                Timestamp.class, entryId).toInstant()).isEqualTo(createdAt);
    }

    @Test
    void rejectsADuplicateSourceEventId() {
        String sourceEventId = UUID.randomUUID().toString();
        insert(sourceEventId, "LOCK_BUYER_DEPOSIT", "BUYER_DEPOSIT", null, null, null, "100.00");
        assertThatThrownBy(() -> insert(sourceEventId, "LOCK_BUYER_DEPOSIT", "BUYER_DEPOSIT", null, null, null, "100.00"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_ledger_source_event");
    }

    @Test
    void rejectsADuplicateRemedyLegId() {
        String decision = UUID.randomUUID().toString();
        String leg = UUID.randomUUID().toString();
        insert(UUID.randomUUID().toString(), "DEPOSIT_FORFEITURE", "SELLER_DEPOSIT", null, decision, leg, "100.00");
        assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), "DEPOSIT_FORFEITURE", "SELLER_DEPOSIT", null, decision, leg, "100.00"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_ledger_remedy_leg");
    }

    @Test
    void allowsManyLegsOfOneDecision() {
        String decision = UUID.randomUUID().toString();
        insert(UUID.randomUUID().toString(), "DEPOSIT_FORFEITURE", "SELLER_DEPOSIT", null, decision, UUID.randomUUID().toString(), "100.00");
        insert(UUID.randomUUID().toString(), "REFUND_TO_BUYER", "BUYER_DEPOSIT", null, decision, UUID.randomUUID().toString(), "100.00");
        assertThat(runtime.queryForObject("SELECT COUNT(*) FROM ledger_entry WHERE remedy_decision_id = ?", Integer.class, decision))
                .isEqualTo(2);
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        for (String amount : new String[] {"0.00", "-0.01", "-100.00"}) {
            assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), "LOCK_BUYER_DEPOSIT", "BUYER_DEPOSIT", null, null, null, amount))
                    .as(amount).isInstanceOf(DataAccessException.class).hasMessageContaining("chk_ledger_amount_positive");
        }
    }

    @Test
    void rejectsUnknownEntryAndFundTypes() {
        // An unknown value also breaks chk_ledger_entry_fund, and MySQL reports whichever check it evaluates first.
        assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), "SEIZE_PENALTY", "BUYER_DEPOSIT", null, null, null, "1.00"))
                .isInstanceOf(DataAccessException.class).hasMessageMatching("(?s).*chk_ledger_entry_(type|fund).*");
        assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), "REFUND_TO_BUYER", "PLATFORM_FEE", null, null, null, "1.00"))
                .isInstanceOf(DataAccessException.class).hasMessageMatching("(?s).*chk_ledger_(fund_type|entry_fund).*");
    }

    /** Domain and schema enforce the same rules independently; this catches one of them being changed alone. */
    @Test
    void schemaAcceptsExactlyWhatTheDomainAccepts() {
        for (EntryType type : EntryType.values()) {
            for (FundType fund : FundType.values()) {
                for (boolean withMilestone : new boolean[] {false, true}) {
                    for (boolean withRemedy : new boolean[] {false, true}) {
                        String milestone = withMilestone ? UUID.randomUUID().toString() : null;
                        String decision = withRemedy ? UUID.randomUUID().toString() : null;
                        String leg = withRemedy ? UUID.randomUUID().toString() : null;
                        boolean domainAccepts = accepts(() -> LedgerEntry.create(UUID.randomUUID().toString(),
                                UUID.randomUUID().toString(), milestone, UUID.randomUUID().toString(), type, fund,
                                decision, leg, new Money(new BigDecimal("1.00")), Instant.now()));
                        boolean schemaAccepts = accepts(() -> insert(UUID.randomUUID().toString(), type.name(),
                                fund.name(), milestone, decision, leg, "1.00"));
                        assertThat(schemaAccepts).as("%s / %s / milestone=%s / remedy=%s", type, fund,
                                withMilestone, withRemedy).isEqualTo(domainAccepts);
                    }
                }
            }
        }
    }

    private static boolean accepts(Runnable action) {
        try {
            action.run();
            return true;
        } catch (InvalidLedgerEntryException | DataAccessException e) {
            return false;
        }
    }

    @Test
    void rejectsARemedyDecisionWithoutALegAndViceVersa() {
        assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), "REFUND_TO_BUYER", "BUYER_DEPOSIT", null, UUID.randomUUID().toString(), null, "1.00"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chk_ledger_remedy_pair");
        assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), "DEPOSIT_FORFEITURE", "SELLER_DEPOSIT", null, null, UUID.randomUUID().toString(), "1.00"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chk_ledger_remedy_pair");
    }

    @Test
    void deniesUpdateAndDeleteToTheRuntimeAccount() {
        String entryId = insert(UUID.randomUUID().toString(), "LOCK_BUYER_DEPOSIT", "BUYER_DEPOSIT", null, null, null, "100.00");
        assertThatThrownBy(() -> runtime.update("UPDATE ledger_entry SET amount = 1.00 WHERE entry_id = ?", entryId))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("UPDATE command denied");
        assertThatThrownBy(() -> runtime.update("DELETE FROM ledger_entry WHERE entry_id = ?", entryId))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("DELETE command denied");
        assertThat(runtime.queryForObject("SELECT amount FROM ledger_entry WHERE entry_id = ?", BigDecimal.class, entryId))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void deniesSchemaChangesToTheRuntimeAccount() {
        assertThatThrownBy(() -> runtime.execute("ALTER TABLE ledger_entry DROP CHECK chk_ledger_amount_positive"))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("denied");
        assertThatThrownBy(() -> runtime.execute("TRUNCATE TABLE ledger_entry"))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("denied");
    }

    private static String insert(String sourceEventId, String entryType, String fundType, String milestoneId,
                                 String remedyDecisionId, String remedyLegId, String amount) {
        String entryId = UUID.randomUUID().toString();
        runtime.update("""
                INSERT INTO ledger_entry (entry_id, source_event_id, contract_id, milestone_id, user_id, entry_type,
                                          fund_type, remedy_decision_id, remedy_leg_id, amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                entryId, sourceEventId, UUID.randomUUID().toString(), milestoneId, UUID.randomUUID().toString(),
                entryType, fundType, remedyDecisionId, remedyLegId, new BigDecimal(amount));
        return entryId;
    }
}
