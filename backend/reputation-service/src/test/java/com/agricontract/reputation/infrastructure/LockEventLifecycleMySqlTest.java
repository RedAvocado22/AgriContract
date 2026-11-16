package com.agricontract.reputation.infrastructure;

import com.agricontract.eventing.EventEnvelopeCodec;
import com.agricontract.eventing.outbox.OutboxPoller;
import com.agricontract.eventing.outbox.OutboxWriter;
import com.agricontract.reputation.application.port.LockEventPublisher;
import com.agricontract.reputation.application.usecase.LockEventLifecycle;
import com.agricontract.reputation.infrastructure.contract.FrozenSchemas;
import com.agricontract.reputation.infrastructure.messaging.ExpireLocksJob;
import com.agricontract.reputation.infrastructure.messaging.OutboxLockEventPublisher;
import com.agricontract.reputation.infrastructure.persistence.JdbcLockStateStore;
import com.agricontract.reputation.infrastructure.persistence.JdbcReputationReadRepository;
import com.agricontract.reputation.infrastructure.persistence.SpringTransactionRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL locks/transactions. Privileged fixture writes stand in for the future #144/#269 ledger writers. */
class LockEventLifecycleMySqlTest {
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    final JdbcTemplate jdbc = ReputationDatabaseFixture.ADMIN;
    final DataSourceTransactionManager manager = new DataSourceTransactionManager(jdbc.getDataSource());
    final SpringTransactionRunner transactions = new SpringTransactionRunner(manager);
    final EventEnvelopeCodec codec = new EventEnvelopeCodec();
    final LockEventPublisher publisher = new OutboxLockEventPublisher(new OutboxWriter(jdbc, codec));
    TestStore store;
    LockEventLifecycle lifecycle;

    static class TestStore extends JdbcLockStateStore {
        volatile Instant time = NOW;
        TestStore(JdbcTemplate jdbc) { super(jdbc); }
        @Override public Instant now() { return time; }
    }
    record Lock(UUID user, UUID entry, UUID decision, UUID source, Instant until) {}

    @BeforeEach void setup() {
        clear();
        store = new TestStore(jdbc);
        lifecycle = new LockEventLifecycle(transactions, store, publisher);
    }
    @AfterEach void clear() {
        for (String table : List.of("lock_event_receipt", "event_outbox", "user_lock_state", "lock_override_event", "lock_entry"))
            jdbc.update("DELETE FROM " + table);
    }
    Lock lock(UUID user, long seconds) {
        return new Lock(user, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(seconds));
    }
    void insert(Lock lock) {
        jdbc.update("""
                INSERT INTO lock_entry (entry_id,source_event_id,remedy_decision_id,contract_id,user_id,penalized_role,
                base_days,repeat_offense_multiplier,track_record_multiplier,zero_progress_multiplier,
                lock_duration_days,locked_until,created_at) VALUES (?,?,?,?,?,'BUYER',30,1,1,1,30,?,?)
                """, lock.entry.toString(), lock.source.toString(), lock.decision.toString(), UUID.randomUUID().toString(),
                lock.user.toString(), time(lock.until), time(NOW));
    }
    boolean create(Lock lock) {
        return lifecycle.lockCreated(lock.user, lock.decision, "NON_DELIVERY", "test-workflow", () -> insert(lock));
    }
    void override(Lock lock, UUID override) {
        jdbc.update("INSERT INTO lock_override_event VALUES (?,?,'UNLOCK_EARLY','approved test fixture',?,?,?)",
                override.toString(), lock.entry.toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), time(NOW));
    }
    long revision(UUID user) {
        return jdbc.queryForObject("SELECT lock_revision FROM user_lock_state WHERE user_id=?", Long.class, user.toString());
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    List<JsonNode> events() {
        return jdbc.query("SELECT envelope FROM event_outbox ORDER BY id", (rs, n) -> {
            try { return new ObjectMapper().readTree(rs.getString(1)); }
            catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
    static LocalDateTime time(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }

    @Test void firstRevisionAndCanonicalLockUseMaximumAndSourceReason() throws Exception {
        UUID user = UUID.randomUUID();
        var longer = lock(user, 120); var shorter = lock(user, 60);
        assertThat(create(longer)).isTrue();
        assertThat(revision(user)).isEqualTo(1);
        assertThat(create(shorter)).isTrue();
        assertThat(revision(user)).isEqualTo(2);
        for (var event : events()) {
            FrozenSchemas.assertEvent("reputationLocked", event);
            assertThat(event.path("payload").path("lockedUntil").asText()).isEqualTo(longer.until.toString());
            assertThat(event.path("payload").path("reasonCode").asText()).isEqualTo("NON_DELIVERY");
        }
        assertThat(events().get(1).path("causationId").asText()).isEqualTo(shorter.source.toString());
    }

    @Test void restartAndReplayDoNotConsumeRevisionEvenAfterOutboxRetention() {
        var lock = lock(UUID.randomUUID(), 60); create(lock);
        jdbc.update("DELETE FROM event_outbox"); // Delivery retention must not erase the business identity.
        var restarted = new LockEventLifecycle(new SpringTransactionRunner(manager), new TestStore(jdbc), publisher);
        assertThat(restarted.lockCreated(lock.user, lock.decision, "NON_DELIVERY", "retry", () -> {
            throw new AssertionError("Duplicate callback ran");
        })).isFalse();
        assertThat(revision(lock.user)).isEqualTo(1);
        assertThat(count("event_outbox")).isZero();
        var next = lock(lock.user, 120);
        assertThat(restarted.lockCreated(next.user, next.decision, "LATE_DELIVERY", "next", () -> insert(next))).isTrue();
        assertThat(revision(lock.user)).isEqualTo(2);
    }

    @Test void concurrentFirstTransitionsHaveUniqueRevisionsAndIndependentUsersStartAtOne() throws Exception {
        UUID user = UUID.randomUUID(); var a = lock(user, 60); var b = lock(user, 120);
        assertThat(ReputationMySqlTest.race(() -> create(a), () -> create(b))).containsExactly(true, true);
        assertThat(events().stream().map(e -> e.path("payload").path("lockRevision").asLong()).toList())
                .containsExactly(1L, 2L);
        var other = lock(UUID.randomUUID(), 60); create(other);
        assertThat(revision(other.user)).isEqualTo(1);
        assertThat(revision(user)).isEqualTo(2);
    }

    @Test void concurrentDuplicateDecisionHasOneReceiptOneEventAndOneRevision() throws Exception {
        var a = lock(UUID.randomUUID(), 60);
        assertThat(ReputationMySqlTest.race(() -> create(a), () -> create(a))).containsExactlyInAnyOrder(true, false);
        assertThat(count("lock_entry")).isEqualTo(1);
        assertThat(count("lock_event_receipt")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
        assertThat(revision(a.user)).isEqualTo(1);
    }

    @Test void conflictingReplayCannotChangeUserOrReason() {
        var a = lock(UUID.randomUUID(), 60); create(a);
        assertThatThrownBy(() -> lifecycle.lockCreated(a.user, a.decision, "SHORTFALL", "retry", () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.lockCreated(UUID.randomUUID(), a.decision, "NON_DELIVERY", "retry", () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("user_lock_state")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
    }

    @Test void failureAfterOutboxInsertRollsBackLedgerStateReceiptAndOutbox() {
        var a = lock(UUID.randomUUID(), 60);
        var reached = new AtomicBoolean();
        var failingStore = new TestStore(jdbc) {
            @Override public void remember(String kind, UUID businessId, UUID user, String reason) {
                super.remember(kind, businessId, user, reason);
                assertThat(count("lock_entry")).isEqualTo(1);
                assertThat(revision(user)).isEqualTo(1);
                assertThat(count("event_outbox")).isEqualTo(1);
                assertThat(count("lock_event_receipt")).isEqualTo(1);
                reached.set(true);
                throw new IllegalStateException("injected after receipt insert");
            }
        };
        var broken = new LockEventLifecycle(transactions, failingStore, publisher);
        for (String table : List.of("lock_entry", "user_lock_state", "lock_event_receipt", "event_outbox"))
            assertThat(count(table)).as(table).isZero();
        assertThatThrownBy(() -> broken.lockCreated(a.user, a.decision, "NON_DELIVERY", "rollback", () -> insert(a)))
                .isInstanceOf(IllegalStateException.class).hasMessage("injected after receipt insert");
        assertThat(reached).isTrue();
        for (String table : List.of("lock_entry", "user_lock_state", "lock_event_receipt", "event_outbox"))
            assertThat(count(table)).as(table).isZero();
        create(a);
        assertThat(revision(a.user)).isEqualTo(1);
    }

    @Test void participatingFailureMarksOuterTransactionRollbackOnly() {
        var outer = new TransactionTemplate(manager);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var outsideWork = lock(UUID.randomUUID(), 120);
        var innerWork = lock(UUID.randomUUID(), 120);
        var reached = new AtomicBoolean();
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            insert(outsideWork);
            assertThatThrownBy(() -> lifecycle.lockCreated(innerWork.user, innerWork.decision, "NON_DELIVERY", "outer", () -> {
                insert(innerWork);
                reached.set(true);
                throw new IllegalStateException("inside participating transaction");
            })).isInstanceOf(IllegalStateException.class).hasMessage("inside participating transaction");
            assertThat(status.isRollbackOnly()).isTrue();
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(reached).isTrue();
        for (String table : List.of("lock_entry", "user_lock_state", "lock_event_receipt", "event_outbox"))
            assertThat(count(table)).as(table).isZero();
    }

    @Test void failedLedgerWriteAndMissingEvidenceNeverProduceAnEvent() {
        var a = lock(UUID.randomUUID(), 60);
        assertThatThrownBy(() -> lifecycle.lockCreated(a.user, a.decision, "NON_DELIVERY", "missing", () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.unlockedEarly(a.user, UUID.randomUUID(), "missing", () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.lockCreated(a.user, a.decision, "NON_DELIVERY", "broken", () -> {
            insert(a); throw new IllegalStateException("ledger failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("event_outbox")).isZero();
        assertThat(count("user_lock_state")).isZero();
        assertThat(count("lock_entry")).isZero();
    }

    @Test void nonMaxExpiryDoesNothingAndFinalExpiryPublishesOneCanonicalUnlockAcrossRestart() throws Exception {
        UUID user = UUID.randomUUID(); var a = lock(user, 60); var b = lock(user, 120);
        create(a); create(b);
        store.time = NOW.plusSeconds(60);
        var job = new ExpireLocksJob(store, lifecycle, 100);
        job.run(); // Actual DB due scan; test business clock verifies the per-user recheck.
        assertThat(count("event_outbox")).isEqualTo(2);
        assertThat(revision(user)).isEqualTo(2);
        var reads = new JdbcReputationReadRepository(jdbc);
        assertThat(reads.publicSummaries(List.of(user), store.time).getFirst().locked()).isTrue();
        store.time = NOW.plusSeconds(120);
        job.run(); job.run();
        var restartedStore = new TestStore(jdbc); restartedStore.time = store.time;
        new ExpireLocksJob(restartedStore, new LockEventLifecycle(new SpringTransactionRunner(manager), restartedStore, publisher), 100).run();
        assertThat(count("event_outbox")).isEqualTo(3);
        assertThat(revision(user)).isEqualTo(3);
        assertThat(reads.publicSummaries(List.of(user), store.time).getFirst().locked()).isFalse();
        assertThat(count("lock_entry")).isEqualTo(2);
        JsonNode event = events().getLast();
        FrozenSchemas.assertEvent("reputationUnlocked", event);
        assertThat(event.path("payload").path("effectiveLockedUntil").isNull()).isTrue();
        assertThat(event.path("payload").path("reasonCode").asText()).isEqualTo("EXPIRED");
        assertThat(event.path("correlationId").asText()).isNotBlank();
        assertThat(event.has("causationId")).isFalse();
    }

    @Test void concurrentTimeoutJobsPersistOneUnlock() throws Exception {
        var a = lock(UUID.randomUUID(), 60); create(a); store.time = a.until;
        var job1 = new ExpireLocksJob(store, lifecycle, 100);
        var secondStore = new TestStore(jdbc); secondStore.time = a.until;
        var job2 = new ExpireLocksJob(secondStore, new LockEventLifecycle(transactions, secondStore, publisher), 100);
        ReputationMySqlTest.race(() -> { job1.run(); return true; }, () -> { job2.run(); return true; });
        assertThat(count("event_outbox")).isEqualTo(2);
        assertThat(revision(a.user)).isEqualTo(2);
    }

    @Test void keysetScanPassesFailuresAndRetriesThemOnNextRun() {
        var locks = new ArrayList<Lock>();
        int i = 5;
        for (int seconds : new int[] {60, 60, 70, 70, 80}) {
            var item = lock(new UUID(0, i--), seconds); locks.add(item); create(item);
        }
        locks.sort(java.util.Comparator.comparing(Lock::until).thenComparing(item -> item.user.toString()));
        jdbc.update("DELETE FROM event_outbox");
        store.time = NOW.plusSeconds(80);
        var attempts = new ArrayList<UUID>();
        var fail = new AtomicBoolean(true);
        var scanning = new LockEventLifecycle(transactions, store, publisher) {
            @Override public boolean expire(UUID user, String correlation) {
                attempts.add(user);
                if (fail.get() && (user.equals(locks.get(0).user) || user.equals(locks.get(1).user)))
                    throw new IllegalStateException("persistent fixture failure");
                return super.expire(user, correlation);
            }
        };
        var job = new ExpireLocksJob(store, scanning, 2);
        job.run();
        assertThat(attempts).containsExactlyElementsOf(locks.stream().map(Lock::user).toList());
        assertThat(count("event_outbox")).isEqualTo(3);
        assertThat(revision(locks.get(0).user)).isEqualTo(1);
        assertThat(revision(locks.get(1).user)).isEqualTo(1);
        for (var item : locks.subList(2, 5)) assertThat(revision(item.user)).isEqualTo(2);
        attempts.clear(); fail.set(false); job.run();
        assertThat(attempts).containsExactly(locks.get(0).user, locks.get(1).user);
        assertThat(count("event_outbox")).isEqualTo(5);
        attempts.clear(); job.run();
        assertThat(attempts).isEmpty();
        assertThat(count("event_outbox")).isEqualTo(5);
    }

    @Test void earlyUnlockAlwaysPublishesAndCarriesRemainingMaximumOrNull() throws Exception {
        UUID user = UUID.randomUUID(); var a = lock(user, 60); var b = lock(user, 120); var c = lock(user, 90);
        create(a); create(b); create(c);
        UUID oa = UUID.randomUUID(), ob = UUID.randomUUID(), oc = UUID.randomUUID();
        assertThat(lifecycle.unlockedEarly(user, oa, "approved", () -> override(a, oa))).isTrue();
        assertThat(events().getLast().path("payload").path("effectiveLockedUntil").asText()).isEqualTo(b.until.toString());
        lifecycle.unlockedEarly(user, ob, "approved", () -> override(b, ob));
        assertThat(events().getLast().path("payload").path("effectiveLockedUntil").asText()).isEqualTo(c.until.toString());
        lifecycle.unlockedEarly(user, oc, "approved", () -> override(c, oc));
        assertThat(events().getLast().path("payload").path("effectiveLockedUntil").isNull()).isTrue();
        assertThat(lifecycle.unlockedEarly(user, oa, "retry", () -> { throw new AssertionError(); })).isFalse();
        for (JsonNode event : events().subList(3, 6)) {
            FrozenSchemas.assertEvent("reputationUnlocked", event);
            assertThat(event.path("payload").path("reasonCode").asText()).isEqualTo("UNLOCKED_EARLY");
        }
        assertThat(revision(user)).isEqualTo(6);
        assertThat(count("lock_entry")).isEqualTo(3);
        assertThat(new JdbcReputationReadRepository(jdbc).publicSummaries(List.of(user), NOW).getFirst().locked()).isFalse();
    }

    @Test void creationHoldingMutexPreventsTimeoutFromClearingTheNewLock() throws Exception {
        var old = lock(UUID.randomUUID(), 60); create(old); store.time = old.until;
        var next = lock(old.user, 120);
        CountDownLatch holding = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var created = pool.submit(() -> lifecycle.lockCreated(next.user, next.decision, "NON_DELIVERY", "race", () -> {
                holding.countDown(); await(release); insert(next);
            }));
            await(holding);
            var expired = pool.submit(() -> { started.countDown(); return lifecycle.expire(old.user, "timeout"); });
            try {
                await(started);
                assertThatThrownBy(() -> expired.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { release.countDown(); }
            assertThat(created.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(expired.get(10, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(count("event_outbox")).isEqualTo(2);
        assertThat(events().getLast().path("payload").path("lockedUntil").asText()).isEqualTo(next.until.toString());
    }

    @Test void timeoutHoldingMutexCommitsBeforeNewCreationAndRevisionsStayOrdered() throws Exception {
        var old = lock(UUID.randomUUID(), 60); create(old); store.time = old.until;
        var next = lock(old.user, 120);
        CountDownLatch holding = new CountDownLatch(1), release = new CountDownLatch(1);
        LockEventPublisher waiting = (user, locked, until, reason, revision, now, correlation, cause) -> {
            holding.countDown(); await(release);
            publisher.publish(user, locked, until, reason, revision, now, correlation, cause);
        };
        var expiring = new LockEventLifecycle(transactions, store, waiting);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var expired = pool.submit(() -> expiring.expire(old.user, "timeout"));
            await(holding);
            var created = pool.submit(() -> create(next));
            try {
                assertThatThrownBy(() -> created.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { release.countDown(); }
            assertThat(expired.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(created.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(events().stream().map(e -> e.path("payload").path("lockRevision").asLong()).toList())
                .containsExactly(1L, 2L, 3L);
        assertThat(events().get(1).path("payload").path("reasonCode").asText()).isEqualTo("EXPIRED");
        assertThat(events().getLast().path("payload").path("lockedUntil").asText()).isEqualTo(next.until.toString());
    }

    @Test void rollbackOfTimeoutLeavesItDueAndRetryUsesSameNextRevision() {
        var a = lock(UUID.randomUUID(), 60); create(a); store.time = a.until;
        LockEventPublisher failing = (u,l,e,r,v,n,c,s) -> { publisher.publish(u,l,e,r,v,n,c,s); throw new IllegalStateException(); };
        assertThatThrownBy(() -> new LockEventLifecycle(transactions, store, failing).expire(a.user, "failed"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(revision(a.user)).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
        assertThat(lifecycle.expire(a.user, "retry")).isTrue();
        assertThat(revision(a.user)).isEqualTo(2);
    }

    @Test void databaseUtcClockAndRuntimePermissionsSupportTimeoutWithoutLedgerWritePrivileges() {
        var runtime = new JdbcTemplate(new DriverManagerDataSource(ReputationDatabaseFixture.MYSQL.getJdbcUrl(),
                "reputation_runtime", "test-only-runtime-password"));
        var runtimeStore = new JdbcLockStateStore(runtime);
        var runtimeTransactions = new SpringTransactionRunner(new DataSourceTransactionManager(runtime.getDataSource()));
        var runtimePublisher = new OutboxLockEventPublisher(new OutboxWriter(runtime, codec));
        var a = lock(UUID.randomUUID(), 60); create(a); // Historical fixture; already due at the real DB clock.
        assertThat(runtimeStore.now()).isBetween(Instant.now().minusSeconds(10), Instant.now().plusSeconds(10));
        assertThat(new LockEventLifecycle(runtimeTransactions, runtimeStore, runtimePublisher).expire(a.user, "real-clock")).isTrue();
        assertThat(revision(a.user)).isEqualTo(2);
        assertThatThrownBy(() -> runtime.update("DELETE FROM lock_event_receipt"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> runtime.update("UPDATE lock_entry SET locked_until=UTC_TIMESTAMP()"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void incompatibleAmbientSnapshotIsRejectedBeforeBusinessWork() {
        var rr = new TransactionTemplate(manager); rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var a = lock(UUID.randomUUID(), 60);
        assertThatThrownBy(() -> rr.execute(status -> create(a))).isInstanceOf(IllegalStateException.class);
        assertThat(count("lock_entry")).isZero();
        transactions.inTransaction(() -> {
            create(a); // Compatible nesting joins the outer transaction.
            assertThat(revision(a.user)).isEqualTo(1);
        });
    }

    @Test void realRabbitRetryAndResendPreserveStoredEventIdentity() throws Exception {
        try (var rabbit = new GenericContainer<>("rabbitmq:3.13.7-management").withExposedPorts(5672)
                .withEnv("RABBITMQ_DEFAULT_USER", "test").withEnv("RABBITMQ_DEFAULT_PASS", "test-password")
                .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))) {
            rabbit.start();
            var connection = new CachingConnectionFactory(rabbit.getHost(), rabbit.getMappedPort(5672));
            connection.setUsername("test"); connection.setPassword("test-password");
            connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            connection.setPublisherReturns(true);
            try {
                var template = new RabbitTemplate(connection); template.setMandatory(true);
                template.setReturnsCallback(returned -> {});
                var admin = new RabbitAdmin(connection);
                var exchange = new TopicExchange("agricontract.reputation"); admin.declareExchange(exchange);
                var a = lock(UUID.randomUUID(), 60); create(a);
                var poller = new OutboxPoller(jdbc, template, new TransactionTemplate(manager), exchange.getName(), 100, Duration.ofSeconds(5));
                poller.poll(); // Unroutable: retained, never silently marked published.
                assertThat(jdbc.queryForObject("SELECT attempts FROM event_outbox", Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT status FROM event_outbox", String.class)).isEqualTo("PENDING");
                Queue queue = new Queue("test.lock-events", false, false, false); admin.declareQueue(queue);
                admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("reputation.*"));
                poller.poll();
                var first = template.receive(queue.getName(), 5000);
                assertThat(first).isNotNull();
                assertThat(jdbc.queryForObject("SELECT status FROM event_outbox", String.class)).isEqualTo("PUBLISHED");
                FrozenSchemas.assertEvent("reputationLocked", new ObjectMapper().readTree(first.getBody()));
                jdbc.update("UPDATE event_outbox SET status='PENDING'"); // Crash after broker ACK, before DB commit.
                poller.poll();
                var replay = template.receive(queue.getName(), 5000);
                assertThat(replay).isNotNull();
                assertThat(replay.getBody()).isEqualTo(first.getBody());
                assertThat(replay.getMessageProperties().getMessageId()).isEqualTo(first.getMessageProperties().getMessageId());
                assertThat(revision(a.user)).isEqualTo(1);
                store.time = a.until;
                assertThat(lifecycle.expire(a.user, "timeout-delivery")).isTrue();
                poller.poll();
                var unlocked = template.receive(queue.getName(), 5000);
                assertThat(unlocked).isNotNull();
                FrozenSchemas.assertEvent("reputationUnlocked", new ObjectMapper().readTree(unlocked.getBody()));
                assertThat(revision(a.user)).isEqualTo(2);
            } finally { connection.destroy(); }
        }
    }

    static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for transaction"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
