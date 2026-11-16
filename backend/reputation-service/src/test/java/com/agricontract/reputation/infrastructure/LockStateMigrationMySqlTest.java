package com.agricontract.reputation.infrastructure;

import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.agricontract.reputation.application.usecase.LockEventLifecycle;
import com.agricontract.reputation.infrastructure.messaging.OutboxLockEventPublisher;
import com.agricontract.reputation.infrastructure.persistence.JdbcLockStateStore;
import com.agricontract.reputation.infrastructure.persistence.SpringTransactionRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real V2-to-V3 upgrade on a disposable MySQL database. */
class LockStateMigrationMySqlTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42")
            .withDatabaseName("migration_test").withUsername("migration_user").withPassword("migration_password")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    static JdbcTemplate jdbc;

    @BeforeAll static void start() {
        MYSQL.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }
    @AfterAll static void stop() { MYSQL.stop(); }
    static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) config.target(target);
        return config.load();
    }
    @BeforeEach void before() { flyway(null).clean(); flyway("2").migrate(); }

    record Lock(UUID entry, UUID decision, UUID source, UUID user, LocalDateTime until) {}
    Lock insert(UUID user, LocalDateTime until) {
        var lock = new Lock(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), user, until);
        jdbc.update("""
                INSERT INTO lock_entry (entry_id, source_event_id, remedy_decision_id, contract_id, user_id,
                penalized_role, repeat_offense_multiplier, track_record_multiplier, zero_progress_multiplier,
                lock_duration_days, locked_until) VALUES (?,?,?,?,?,'BUYER',1,1,1,30,?)
                """, lock.entry.toString(), lock.source.toString(), lock.decision.toString(), UUID.randomUUID().toString(),
                user.toString(), until);
        return lock;
    }
    void override(Lock lock) {
        jdbc.update("""
                INSERT INTO lock_override_event (event_id,lock_entry_id,override_type,reason,proposed_by,approved_by)
                VALUES (?,?,'UNLOCK_EARLY','approved',?,?)
                """, UUID.randomUUID().toString(), lock.entry.toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void emptyLedgerIsNoOp() {
        flyway(null).migrate();
        assertThat(count("user_lock_state")).isZero();
        assertThat(count("event_outbox")).isZero();
    }

    @Test void upgradeBackfillsOnlyEffectiveLocksAndFirstEventStartsAtOne() throws Exception {
        LocalDateTime now = jdbc.queryForObject("SELECT UTC_TIMESTAMP()", LocalDateTime.class);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        insert(a, now.plusDays(10));
        LocalDateTime expected = now.plusDays(20);
        insert(a, expected);
        override(insert(a, now.plusDays(30)));
        insert(b, now.minusDays(10));
        override(insert(c, now.plusDays(40)));

        flyway(null).migrate();
        assertThat(count("user_lock_state")).isEqualTo(1);
        var row = jdbc.queryForMap("SELECT user_id, lock_revision, effective_locked_until FROM user_lock_state");
        assertThat(row.get("user_id")).isEqualTo(a.toString());
        assertThat(((Number) row.get("lock_revision")).longValue()).isZero();
        assertThat(jdbc.queryForObject("SELECT effective_locked_until FROM user_lock_state WHERE user_id=?",
                LocalDateTime.class, a.toString())).isEqualTo(expected);
        assertThat(count("event_outbox")).isZero();

        var lifecycle = new LockEventLifecycle(new SpringTransactionRunner(new DataSourceTransactionManager(jdbc.getDataSource())),
                new JdbcLockStateStore(jdbc), new OutboxLockEventPublisher(new OutboxWriter(jdbc, new EventEnvelopeCodec())));
        UUID decision = UUID.randomUUID(), source = UUID.randomUUID();
        assertThat(lifecycle.lockCreated(a, decision, "NON_DELIVERY", "migration-test", () -> jdbc.update("""
                INSERT INTO lock_entry (entry_id,source_event_id,remedy_decision_id,contract_id,user_id,penalized_role,
                repeat_offense_multiplier,track_record_multiplier,zero_progress_multiplier,lock_duration_days,locked_until)
                VALUES (?,?,?,?,?,'BUYER',1,1,1,30,?)
                """, UUID.randomUUID().toString(), source.toString(), decision.toString(), UUID.randomUUID().toString(),
                a.toString(), now.plusDays(50)))).isTrue();
        assertThat(jdbc.queryForObject("SELECT lock_revision FROM user_lock_state WHERE user_id=?", Long.class, a.toString())).isEqualTo(1L);
        assertThat(count("event_outbox")).isEqualTo(1);
        var event = new ObjectMapper().readTree(jdbc.queryForObject("SELECT envelope FROM event_outbox", String.class));
        assertThat(event.path("payload").path("lockRevision").asLong()).isEqualTo(1L);
        assertThat(event.path("payload").path("lockedUntil").asText()).isEqualTo(now.plusDays(50).toInstant(java.time.ZoneOffset.UTC).toString());
    }
}
